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
public class ForStatement extends Statement {
  private final Statement init;
  private final Expression condition;
  private final Expression update;
  private final BlockStatement block;

  public ForStatement(
    Location loc, Statement init, Expression condition, Expression update, BlockStatement block
  ) {
    this.init = init;
    this.condition = condition;
    this.update = update;
    this.block = block;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public Statement getInit() {
    return init;
  }

  public Expression getCondition() {
    return condition;
  }

  public Expression getUpdate() {
    return update;
  }

  public BlockStatement getBlock() {
    return block;
  }  
}