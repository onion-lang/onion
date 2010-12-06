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
public class ReturnStatement extends Statement {
  private final Expression expression;

  public ReturnStatement(Location loc, Expression returned) {
    this.expression = returned;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return expression
   */
  public Expression getExpression() {
    return expression;
  }
}