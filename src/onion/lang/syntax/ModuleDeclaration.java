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


import onion.compiler.Location;
import onion.lang.syntax.visitor.ASTVisitor;

;


/**
 * @author Kota Mizushima
 *  
 */
public class ModuleDeclaration extends AstNode {  
  private final String name;

  public ModuleDeclaration(Location loc, String name) {
    this.name = name;
    setLocation(loc);
  }

  public String getName() {
    return name;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  
}