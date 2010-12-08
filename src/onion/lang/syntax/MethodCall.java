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
public class MethodCall extends Expression {
  
  private Expression target;

  private String name;

  private Expression[] arguments;

  public MethodCall(Expression target, String name, Expression[] arguments) {
    this.target = target;
    this.name = name;
    this.arguments = arguments;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public Expression target() {
    return target;
  }

  public String name() {
    return name;
  }

  public boolean isCallSuper() {
    return target == null;
  }

  public Expression[] getArguments() {
    return arguments;
  }
}