/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;


/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class BasicTypeRef implements TypeRef {
  public static final BasicTypeRef BYTE = new BasicTypeRef("byte");
  public static final BasicTypeRef SHORT = new BasicTypeRef("short");
  public static final BasicTypeRef CHAR = new BasicTypeRef("char");
  public static final BasicTypeRef INT = new BasicTypeRef("int");
  public static final BasicTypeRef LONG = new BasicTypeRef("long");
  public static final BasicTypeRef FLOAT = new BasicTypeRef("float");
  public static final BasicTypeRef DOUBLE = new BasicTypeRef("double");
  public static final BasicTypeRef BOOLEAN = new BasicTypeRef("boolean");
  public static final BasicTypeRef VOID = new BasicTypeRef("void");  
  
  private String name;
  
  private BasicTypeRef(String name) {
    this.name = name;
  }
  
  public String getName(){
    return name;
  }
  
  public boolean isNumeric(){
    return isInteger() && isReal();
  }
  
  public boolean isInteger(){
    return this == BYTE || this == SHORT || this == INT || this == LONG;
  }
  
  public boolean isReal(){
    return this == FLOAT || this == DOUBLE;
  }
  
  public boolean isBoolean(){
    return this == BOOLEAN;
  }
  
  public boolean isArrayType() {
    return false;
  }
  
  public boolean isBasicType() {
    return true;
  }
  
  public boolean isClassType() {
    return false;
  }
  
  public boolean isNullType() {
    return false;
  }

  public boolean isObjectType() {
    return false;
  }
}