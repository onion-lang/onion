/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
public class IrSynchronized implements IrStatement {
  public IrExpression expression;
  public IrStatement statement;
  public IrSynchronized(IrExpression expression, IrStatement statement){
    this.expression = expression;
    this.statement = statement;
  }
}
