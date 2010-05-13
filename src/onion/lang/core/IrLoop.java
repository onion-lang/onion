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
 * Date: 2005/04/17
 */
public class IrLoop implements IrStatement {  
  public final IrExpression condition;  
  public final IrStatement stmt;
  
  public IrLoop(IrExpression condition, IrStatement stmt) {
    this.condition = condition;
    this.stmt = stmt;
  }
}
