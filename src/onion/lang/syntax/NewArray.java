/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
/*
 * Created on 2004/12/02
 */
package onion.lang.syntax;


import onion.lang.syntax.visitor.ASTVisitor;


/**
 * @author Kota Mizushima
 *  
 */
public class NewArray extends Expression {
  private final TypeSpec type;
  private final Expression[] arguments;

  public NewArray(Location loc, TypeSpec type, Expression[] arguments) {
    this.type = type;
    this.arguments = arguments;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public Expression[] getArguments(){
    return (Expression[])arguments.clone();
  }
  
  public TypeSpec getType() {
    return type;
  }  
}