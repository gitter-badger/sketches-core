/*
 * Copyright 2015, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */
package com.yahoo.sketches.theta;

import static com.yahoo.sketches.theta.CompactSketch.compactCache;
import static com.yahoo.sketches.HashOperations.hashArrayInsert;
import static com.yahoo.sketches.HashOperations.hashSearch;
import static java.lang.Math.min;

import java.util.Arrays;

import com.yahoo.sketches.Family;
import com.yahoo.sketches.Util;
import com.yahoo.sketches.memory.Memory;

/**
 * @author Lee Rhodes
 * @author Kevin Lang
 */
class HeapAnotB extends SetOperation implements AnotB {
  private final short seedHash_;
  private Sketch a_;
  private Sketch b_;
  private long thetaLong_;
  private boolean empty_; 
  private long[] cache_; // no match set
  private int curCount_ = 0;
  
  private int lgArrLongsHT_; //for Hash Table only. may not need to be member after refactoring
  private long[] bHashTable_; //may not need to be member after refactoring.
  
  /**
   * Construct a new Union SetOperation on the java heap.  Called by SetOperation.Builder.
   * 
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   */
  HeapAnotB(long seed) {
    seedHash_ = computeSeedHash(seed);
  }
  
  @Override
  public void update(Sketch a, Sketch b) { 
    a_ = a;
    b_ = b;
    thetaLong_ = Long.MAX_VALUE;
    empty_ = true;
    cache_ = null;
    curCount_ = 0;
    lgArrLongsHT_ = 5;
    bHashTable_ = null;
    compute();
  }
  
  @Override
  public CompactSketch getResult(boolean dstOrdered, Memory dstMem) {
    long[] compactCache = (curCount_ <= 0)? new long[0] : Arrays.copyOfRange(cache_, 0, curCount_);
    if (dstOrdered && (curCount_ > 1)) {
      Arrays.sort(compactCache);
    }
    //Create the CompactSketch
    CompactSketch comp = CompactSketch.createCompactSketch(compactCache, empty_, seedHash_, curCount_, 
        thetaLong_, dstOrdered, dstMem);
    reset();
    return comp;
  }
  
  @Override
  public CompactSketch getResult() {
    return getResult(true, null);
  }
  
  @Override
  public Family getFamily() {
    return Family.A_NOT_B;
  }
  
  //restricted
  
  void compute() {
    int swA = (a_ == null)? 0 : (a_.isEmpty())? 1: (a_ instanceof UpdateSketch)? 4 : (a_.isOrdered())? 3 : 2;
    int swB = (b_ == null)? 0 : (b_.isEmpty())? 1: (b_ instanceof UpdateSketch)? 4 : (b_.isOrdered())? 3 : 2;
    int sw = (swA * 8) | swB;
    
    //  NOTES:
    //    In the table below, A and B refer to the two input sketches in the order A-not-B.
    //    The Theta rule: min( ThetaA, ThetaB)
    //    The Empty rule: Whatever A is: E(a)
    //    The Return triple is defined as: (Theta, Count, EmptyFlag).
    //    bHashTable temporarily stores the values of B.
    //    A sketch in stored form can be in one of 5 states
    //    Null is not actually a state, but is included for completeness.
    //    Null is interpreted as {Theta = 1.0, count = 0, empty = true}.
    //    The empty state may have Theta < 1.0, but count must be zero.
    //    State:
    //      0 N null
    //      1 E Empty
    //      2 C Compact, not ordered
    //      3 O Compact Ordered
    //      4 H Hash-Table
    //
    //A    B    swA  swB  Case  Action
    //N    N    0    0    0     Return (1.0, 0, T)
    //N    E    0    1    1     Return B: (ThB, 0, T)
    //N    C    0    2    2     Return (ThB, 0, T)
    //N    O    0    3    3     Return (ThB, 0, T)
    //N    H    0    4    4     Return (ThB, 0, T)
    //E    N    1    0    8     Return A: (ThA, 0, T)
    //E    E    1    1    9     Return (min, 0, T)
    //E    C    1    2    10    Return (min, 0, T)
    //E    O    1    3    11    Return (min, 0, T)
    //E    H    1    4    12    Return (min, 0, T)
    //C    N    2    0    16    Return A: (ThA, |A|, E(a))
    //C    E    2    1    17    Return (min, |A|, E(a))
    //C    C    2    2    18    B -> H; => C,H
    //C    O    2    3    19    B -> H; => C,H
    //C    H    2    4    20    scan all A, search B, on nomatch -> list (same as HH)
    //O    N    3    0    24    Return A: (ThA, |A|, E(a))
    //O    E    3    1    25    Return (min, |A|, E(a))
    //O    C    3    2    26    B -> H; => O,H
    //O    O    3    3    27    B -> H; => O,H
    //O    H    3    4    28    scan A early stop, search B, on nomatch -> list
    //H    N    4    0    32    Return A: (ThA, |A|, E(a))
    //H    E    4    1    33    Return (min, |A|, E(a))
    //H    C    4    2    34    B -> H; => H,H
    //H    O    4    3    35    B -> H; => H,H
    //H    H    4    4    36    scan all A, search B, on nomatch -> list
    
    switch (sw) {
      case 0 : { //A and B are null.  
        thetaLong_ = Long.MAX_VALUE;
        empty_ = true;
        break; //{1.0, 0, T}
      }
      case 1: 
      case 2: 
      case 3: 
      case 4:  { //A is null, B is valid
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = b_.getThetaLong();
        empty_ = true;
        break; //{ThB, 0, T}
      }
      case 8: { //A is empty, B is null
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        thetaLong_ = a_.getThetaLong();
        empty_ = true;
        break; //{ThA, 0, T}
      }
      case 9: 
      case 10: 
      case 11: 
      case 12: { //A empty, B valid
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = min(a_.getThetaLong(), b_.getThetaLong());
        empty_ = true;
        break; //{min, 0, T}
      }
      case 16: 
      case 24: 
      case 32: { //A valid, B null
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        thetaLong_ = a_.getThetaLong();
        empty_ = a_.isEmpty();
        //move A to cache
        curCount_ = a_.getRetainedEntries(true);
        cache_ = compactCache(a_.getCache(), curCount_, thetaLong_, false);
        break; //(min, 0, Ea)
      }
      case 17: 
      case 25: 
      case 33: { //A valid, B empty
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = min(a_.getThetaLong(), b_.getThetaLong());
        empty_ = a_.isEmpty();
        //move A to cache
        curCount_ = a_.getRetainedEntries(true);
        cache_ = compactCache(a_.getCache(), curCount_, thetaLong_, false); 
        break; //(min, 0, Ea)
      }
      case 18: 
      case 19: 
      case 34: 
      case 35: {//A compact or HT, B compact or ordered 
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = min(a_.getThetaLong(), b_.getThetaLong());
        empty_ = a_.isEmpty();
        //must convert B to HT
        convertBtoHT(); //builds HT from B
        scanAllAsearchB(); //builds cache, curCount from A, HT
        break; //(min, n, Ea)
      }
      case 26: 
      case 27: { //A ordered early stop, B compact or ordered 
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = min(a_.getThetaLong(), b_.getThetaLong());
        empty_ = a_.isEmpty();
        convertBtoHT(); //builds HT from B
        scanEarlyStopAsearchB();
        break; //(min, n, Ea)
      }
      case 20: 
      case 36: { //A compact or HT, B is already HT
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = min(a_.getThetaLong(), b_.getThetaLong());
        empty_ = a_.isEmpty();
        //b is already HT
        lgArrLongsHT_ = ((UpdateSketch)b_).getLgArrLongs();
        bHashTable_ = b_.getCache(); //safe as bHashTable is read-only
        scanAllAsearchB(); //builds cache, curCount from A, HT
        break; //(min, n, Ea)
      }
      case 28: { //A ordered early stop, B is already hashtable
        Util.checkSeedHashes(seedHash_, a_.getSeedHash());
        Util.checkSeedHashes(seedHash_, b_.getSeedHash());
        thetaLong_ = min(a_.getThetaLong(), b_.getThetaLong());
        empty_ = a_.isEmpty();
        //b is already HT
        lgArrLongsHT_ = ((UpdateSketch)b_).getLgArrLongs();
        bHashTable_ = b_.getCache(); //safe as bHashTable is read-only
        scanEarlyStopAsearchB();
        break; //(min, n, Ea)
      }
    }
  }
  
  private void convertBtoHT() {
    int curCountB = b_.getRetainedEntries(true);
    lgArrLongsHT_ = computeMinLgArrLongsFromCount(curCountB);
    bHashTable_ = new long[1 << lgArrLongsHT_];
    int count = hashArrayInsert(b_.getCache(), bHashTable_, lgArrLongsHT_, thetaLong_);
    assert (count == curCountB);
  }
  
  //Sketch A is either unordered compact or hash table
  private void scanAllAsearchB() {
    long[] scanAArr = a_.getCache();
    int arrLongsIn = scanAArr.length;
    cache_ = new long[arrLongsIn];
    for (int i = 0; i < arrLongsIn; i++ ) {
      long hashIn = scanAArr[i];
      if ((hashIn <= 0L) || (hashIn >= thetaLong_)) continue;
      int foundIdx = hashSearch(bHashTable_, lgArrLongsHT_, hashIn);
      if (foundIdx > -1) continue;
      cache_[curCount_++] = hashIn;
    }
  }
  
  //Sketch A is ordered compact, which enables early stop
  private void scanEarlyStopAsearchB() {
    long[] scanAArr = a_.getCache();
    int arrLongsIn = scanAArr.length;
    cache_ = new long[arrLongsIn]; //maybe 2x what is needed, but getRetainedEntries can be slow.
    for (int i = 0; i < arrLongsIn; i++ ) {
      long hashIn = scanAArr[i];
      if (hashIn <= 0L) continue;
      if (hashIn >= thetaLong_) {
        break; //early stop assumes that hashes in input sketch are ordered!
      }
      int foundIdx = hashSearch(bHashTable_, lgArrLongsHT_, hashIn);
      if (foundIdx > -1) continue;
      cache_[curCount_++] = hashIn;
    }
  }
  
  private void reset() {
    a_ = null;
    b_ = null;
    thetaLong_ = Long.MAX_VALUE;
    empty_ = true;
    cache_ = null;
    curCount_ = 0;
    lgArrLongsHT_ = 5;
    bHashTable_ = null;
  }
  
}
