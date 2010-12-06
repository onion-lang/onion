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
public class TypeSpec extends AstNode {
  private final RawTypeNode component;
  private final int dimension;
  private final TypeSpec[] typeArguments;
  
  public TypeSpec(RawTypeNode component, int dimension, TypeSpec[] typeArguments) {
    this.component = component;
    this.dimension = dimension;
    this.typeArguments = typeArguments;
  }
  
  public TypeSpec[] typeArguments() {
    return typeArguments;
  }
  
  public RawTypeNode component() {
    return component;
  }
  
  public String getComponentName(){
    return component.name();
  }
  
  public int componentKind(){
    return component.kind();
  }
  
  public int dimension() {
    return dimension;
  }
  
  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}