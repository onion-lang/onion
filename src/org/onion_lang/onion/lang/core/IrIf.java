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
public class IrIf implements IrStatement{
  private final IrExpression condition;
  private final IrStatement thenStatement, elseStatement;

  public IrIf(
    IrExpression condition, 
    IrStatement thenStatement, IrStatement elseStatement){
    this.condition = condition;
    this.thenStatement = thenStatement;
    this.elseStatement = elseStatement;
  }

  public IrExpression getCondition() {
    return condition;
  }

  public IrStatement getThenStatement() {
    return thenStatement;
  }

  public IrStatement getElseStatement() {
    return elseStatement;
  }
}
