/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.BasicTypeRef;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrChar extends IrExpression {
  private final char value;
  
  public IrChar(char value) {
    this.value = value;
  }
  
  public char getValue() {
    return value;
  }
  
  public TypeRef type() { 
    return BasicTypeRef.CHAR; 
  }
}
