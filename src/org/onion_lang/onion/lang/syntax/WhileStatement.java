/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *
 */
public class WhileStatement extends Statement {
  private final Expression condition;
  private final BlockStatement block;

  /**
   * @param parent
   */
  public WhileStatement(Location loc, Expression condition, BlockStatement thenBlock) {
    this.condition = condition;
    this.block = thenBlock;
    setLocation(loc);
  }

  /**
   * @return condition
   */
  public Expression getCondition() {
    return condition;
  }

  /**
   * @return block
   */
  public BlockStatement getBlock() {
    return block;
  }
  
  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}