/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
/*
 * Created on 2004/12/02
 */
package onion.lang.syntax;


import onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class Argument extends AstNode {
  private final String name;  
  private final TypeSpec type;

  public Argument(Location location, String name, TypeSpec type) {
    this.name = name;
    this.type = type;
    setLocation(location);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public String getName() {
    return name;
  }

  public TypeSpec getType() {
    return type;
  }
    
}