/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;


import onion.lang.syntax.visitor.ASTVisitor;

;

/**
 * @author Kota Mizushima
 *
 */
public class MethodDeclaration extends MemberDeclaration {
  private final Argument[] arguments;
  private final TypeSpec returnType;
  private final BlockStatement block;

  public MethodDeclaration(
    Location loc, String name, Argument[] arguments, TypeSpec returnType, BlockStatement block
  ) {
    super(name);
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
}