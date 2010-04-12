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
public class LocalVariableDeclaration extends Statement {
  private String name;
  private TypeSpec type;
  private Expression init;

  public LocalVariableDeclaration(Location loc, String name, TypeSpec type, Expression init) {
    this.name = name;
    this.type = type;
    this.init = init;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public String getName() {
    return name;
  }

  public TypeSpec getType(){
    return type;
  }

  public Expression getInit() {
    return init;
  }
}