/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrReturn implements IrStatement {
  public final IrExpression expression;  
  
  public IrReturn(IrExpression expression) {
    this.expression = expression;
  }
}
