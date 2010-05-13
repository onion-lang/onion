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
public class Cast extends Expression {
  private final Expression target;
  private final TypeSpec type;

  public Cast(Expression target, TypeSpec convertType) {
    this.target = target;
    this.type = convertType;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public TypeSpec getType() {
    return type;
  }

  public Expression getTarget() {
    return target;
  }
}