/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.env;

import org.onion_lang.onion.lang.core.type.TypeRef;


/**
 * @author Kota Mizushima
 */
public class LocalBinding{  
  private final int index;  
  private final TypeRef type;
  
  public LocalBinding(int index, TypeRef type){
    this.index	= index;
    this.type = type;
  }

  public int getIndex() {
    return index;
  }

  public TypeRef getType() {
    return type;
  }
}