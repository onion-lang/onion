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
public class StaticMethodCall extends Expression {
  private final TypeSpec target;
  private final String name;
  private final Expression[] args;

  public StaticMethodCall(
    Location loc, TypeSpec target, String name, Expression[] args
  ) {
    this.target = target;
    this.name = name;
    this.args = args;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public TypeSpec getTarget() {
    return target;
  }
  
  public String getName() {
    return name;
  }  

  public Expression[] getArgs() {
    return (Expression[])args.clone();
  }  
}