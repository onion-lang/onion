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