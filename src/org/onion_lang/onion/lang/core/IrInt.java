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
public class IrInt extends IrExpression {
  private int value;
  public IrInt(int value){
    this.value = value;
  }
  public int getValue() {
    return value;
  }
  
  public TypeRef type() { return BasicTypeRef.INT; }
}
