/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrCast extends IrExpression {
  private final IrExpression target;
  private final TypeRef conversion;

  public IrCast(IrExpression target, TypeRef conversion) {
    this.target = target;
    this.conversion = conversion;
  }
  
  public TypeRef getConversion() {
    return conversion;
  }
  
  public IrExpression getTarget() {
    return target;
  }
  
  public TypeRef type() {
    return conversion;
  }
}