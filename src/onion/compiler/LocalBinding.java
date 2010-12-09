/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;


/**
 * @author Kota Mizushima
 */
public class LocalBinding{  
  private final int index;  
  private final IRT.TypeRef type;
  
  public LocalBinding(int index, IRT.TypeRef type){
    this.index	= index;
    this.type = type;
  }

  public int getIndex() {
    return index;
  }

  public IRT.TypeRef getType() {
    return type;
  }
}