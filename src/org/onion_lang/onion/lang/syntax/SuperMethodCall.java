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
 * Date: 2005/07/10
 */
public class SuperMethodCall extends Expression {
  private final String name;
  private final Expression[] params;

  public SuperMethodCall(Location loc, String name, Expression[] params) {
    this.name = name;
    this.params = params;
    setLocation(loc);
  }
  
  public String getName(){
    return name;
  }
  
  public Expression[] getParams(){
    return params;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}
