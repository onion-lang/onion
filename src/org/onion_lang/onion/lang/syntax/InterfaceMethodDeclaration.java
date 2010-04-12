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
public class InterfaceMethodDeclaration extends AstNode {
  private final String name;
  private final Argument[] arguments;
  private final TypeSpec returnType;

  public InterfaceMethodDeclaration(
    Location loc, String name, Argument[] arguments, TypeSpec returnType
  ) {
    this.name = name;
    this.arguments = arguments;
    this.returnType = returnType;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public String getName(){
    return name;
  }
  
  public TypeSpec getReturnType() {
    return returnType;
  }

  public Argument[] getArguments() {
    return arguments;
  }  
}