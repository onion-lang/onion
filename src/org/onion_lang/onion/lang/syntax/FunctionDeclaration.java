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
 * Represents top level method.
 * @author Kota Mizushima
 *
 */
public class FunctionDeclaration extends TopLevelElement {  
  private final int modifier;  
  private final String name;  
  private final Argument[] arguments;  
  private final TypeSpec returnType;  
  private final BlockStatement block;

  public FunctionDeclaration(
    Location loc, int modifier, String name, Argument[] arguments, TypeSpec returnType, BlockStatement block
  ) {
    this.modifier = modifier;
    this.name = name;
    this.block = block;
    this.arguments = arguments;
    this.returnType = returnType;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public Argument[] getArguments(){
    return (Argument[])arguments.clone();
  }
  
  public TypeSpec getReturnType(){
    return returnType;
  }

  public BlockStatement getBlock() {
    return block;
  }
  
  public int getModifier(){
    return modifier;
  }

  public String getName() {
    return name;
  }
}