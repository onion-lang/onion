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
 * This class is abstract super class of any AstNode representing type.
 * @author Kota Mizushima
 * 
 */
public abstract class TypeNode extends AstNode {    
  public TypeNode(Location loc) {
    super(loc);
  }
  
  public abstract String name();
}