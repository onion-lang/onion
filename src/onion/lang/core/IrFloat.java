/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.BasicTypeRef;
import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrFloat extends IrExpression {
  private final float value;
  
  public IrFloat(float value){
    this.value = value;
  }
  
  public float getValue() { return value; }
  
  public TypeRef type() { return BasicTypeRef.FLOAT; }
}