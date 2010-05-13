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
 * @author Kota Mizushima
 *  
 */
public class ConstructorDeclaration extends MemberDeclaration {  
  private final Argument[] arguments;
  private final BlockStatement block;
  private final Expression[] initializers;
  
  public ConstructorDeclaration(
    Location loc, Argument[] arguments, Expression[] initializers, BlockStatement block
  ) {
    super("new");
    this.arguments = arguments;
    this.block = block;
    this.initializers = initializers;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public BlockStatement getBody() {
    return block;
  }
  
  public Expression[] getInitializers(){
    return (Expression[])initializers.clone();
  }
  
  public Argument[] getArguments(){
    return (Argument[])arguments.clone();
  }
}