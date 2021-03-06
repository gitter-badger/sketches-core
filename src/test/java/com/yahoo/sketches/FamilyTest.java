/*
 * Copyright 2015, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */
package com.yahoo.sketches;

import static com.yahoo.sketches.Family.*;
import static org.testng.Assert.*;

import org.testng.annotations.Test;

import com.yahoo.sketches.Family;
//import com.yahoo.sketches.theta.Sketch;
import com.yahoo.sketches.theta.UpdateSketch;

/**
 * @author Lee Rhodes
 */
public class FamilyTest {
  
  @Test
  public void checkFamilyEnum() {
    Family[] families = Family.values();
    int numFam = families.length;
    
    for (int i=0; i<numFam; i++) {
      Family f = families[i];
      int fid = f.getID();
      f.checkFamilyID(fid);
      
      Family f2 = idToFamily(fid);
      assertTrue(f.equals(f2));
      assertEquals(f.getFamilyName(), f2.getFamilyName());
      int id2 = f2.getID();
      assertEquals(fid, id2);
    }
    checkStringToFamily("Alpha");
    checkStringToFamily("QuickSelect");
    checkStringToFamily("Union");
    checkStringToFamily("Intersection");
    checkStringToFamily("AnotB");
    checkStringToFamily("HLL");
    checkStringToFamily("Quantiles");
  }
  
  private static void checkStringToFamily(String inStr) {
    String fName = stringToFamily(inStr).toString();
    assertEquals(fName, inStr.toUpperCase());
  }
  
  @Test
  public void checkFamily() {
    UpdateSketch sk = UpdateSketch.builder().build();
    String sname = sk.getClass().getSimpleName();
    String fname = Family.objectToFamily(sk).toString();
    assertTrue(sname.toUpperCase().contains(fname));
    
//    for (Family f : Family.values()) {
//      String fstr = f.toString();
//      println("Name: "+fstr + ": ID: "+f.getID());
//    }
  }
  
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void checkBadFamilyName() {
    stringToFamily("Test");
  }
  
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void checkBadObject() {
    objectToFamily("Test");
  }
  
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void checkBadFamilyID() {
    Family famAlpha = Family.ALPHA;
    Family famQS = Family.QUICKSELECT;
    famAlpha.checkFamilyID(famQS.getID());
  }
  
  @Test
  public void printlnTest() {
    println("PRINTING: "+this.getClass().getName());
  }
  
  /**
   * @param s value to print 
   */
  static void println(String s) {
    //System.out.println(s); //disable here
  }
  
}
