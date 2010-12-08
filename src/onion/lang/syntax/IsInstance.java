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
public class IsInstance extends Expression {
  private final Expression target;
  private final TypeSpec type;

  public IsInstance(Expression target, TypeSpec type) {
    this.target = target;
    this.type = type;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return target
   */
  public Expression target() {
    return target;
  }

  /**
   * @return type
   */
  public TypeSpec destinationType() {
    return type;
  }
}