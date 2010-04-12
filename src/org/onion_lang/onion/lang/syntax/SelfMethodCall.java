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
public class SelfMethodCall extends Expression {
  private String name;
  private Expression[] arguments;

  public SelfMethodCall(Location loc, String name, Expression[] arguments){
    this.name = name;
    this.arguments = arguments;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public String getName() {
    return name;
  }

  public Expression[] getArguments() {
    return arguments;
  } 
}