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
 * クロージャを表現する構文木のノード
 * @author Kota Mizushima
 * Date: 2005/04/10
 */
public class ClosureExpression extends Expression{
  private final TypeSpec type;  
  private final String name;  
  private final Argument[] arguments;  
  private final TypeSpec returnType;
  private final BlockStatement block;
  
  public ClosureExpression(
    Location loc, TypeSpec type, String name, Argument[] arguments, 
    TypeSpec returnType, BlockStatement block
  ) {
    this.type = type;
    this.name = name;
    this.arguments = arguments;
    this.returnType = returnType;
    this.block = block;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
    
  public TypeSpec getType(){
    return type;
  }
  
  public String getName() {
    return name;
  }
  
  public Argument[] getArguments() {
    return (Argument[])arguments.clone();
  }
  
  public TypeSpec getReturnType() {
    return returnType;
  }
  
  public BlockStatement getBlock(){
    return block;
  }
}
