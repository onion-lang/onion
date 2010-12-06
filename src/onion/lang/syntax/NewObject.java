/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax;

import onion.compiler.Location;
import onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class NewObject extends Expression {  
  private final TypeSpec type;
  private final Expression[] arguments;

  public NewObject(Location loc, TypeSpec type, Expression[] arguments) {
    this.type = type;
    this.arguments = arguments;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public TypeSpec getType(){
    return type;
  }  

  public Expression[] getArguments() {
    return arguments;
  }
}