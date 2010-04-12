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
public class GlobalVariableDeclaration extends TopLevelElement {
  private final int modifier;  
  private final String name;  
  private final Expression init;
  private final TypeSpec type;

  public GlobalVariableDeclaration(
    Location loc, int modifier, String name, TypeSpec type, Expression init
  ) {
    this.modifier = modifier;
    this.name = name;
    this.init = init;
    this.type = type;
    setLocation(loc);
  }
  
  public int getModifier(){
    return modifier;
  }
  
  public String getName(){
    return name;
  }

  public TypeSpec getType() {
    return type;
  }
  
  public Expression getInit() {
    return init;
  }
  
  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}