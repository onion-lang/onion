/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;


/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class NullTypeRef implements TypeRef {
  public static NullTypeRef NULL = new NullTypeRef("null");
  
  private String name;
  
  private NullTypeRef(String name) {
    this.name = name;
  }
  
  public String getName(){
    return name;
  }
  
  public boolean isArrayType() {
    return false;
  }
  
  public boolean isBasicType() {
    return false;
  }
  
  public boolean isClassType() {
    return false;
  }
  
  public boolean isNullType() {
    return true;
  }
  
  public boolean isObjectType(){
    return false;
  }
}