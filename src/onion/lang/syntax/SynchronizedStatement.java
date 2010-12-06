/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;

import onion.compiler.Location;
import onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class SynchronizedStatement extends Statement {
  private final Expression target;
  private final BlockStatement block;
  
  public SynchronizedStatement(Location loc, Expression condition, BlockStatement thenBlock) {
    this.target = condition;
    this.block = thenBlock;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return target
   */
  public Expression getTarget() {
    return target;
  }

  /**
   * @return block
   */
  public BlockStatement getBlock() {
    return block;
  }
}