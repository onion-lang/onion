/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;

import onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class IfStatement extends Statement {
  private final Expression condition;
  private final BlockStatement thenBlock;
  private final BlockStatement elseBlock;

  public IfStatement(
    Location loc, Expression condition, BlockStatement thenBlock, BlockStatement elseBlock
  ) {
    this.condition = condition;
    this.thenBlock = thenBlock;
    this.elseBlock = elseBlock;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return condition
   */
  public Expression getCondition() {
    return condition;
  }

  /**
   * @return thenBlock
   */
  public BlockStatement getThenBlock() {
    return thenBlock;
  }

  /**
   * @return elseBlock
   */
  public BlockStatement getElseBlock() {
    return elseBlock;
  }
}