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
public class FieldDeclaration extends MemberDeclaration {
  private final Expression initializer;  
  private final TypeSpec type;

  public FieldDeclaration(String name, TypeSpec type, Expression initializer) {
    super(name);
    this.initializer = initializer;
    this.type = type;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
  
  public TypeSpec getType() {
    return type;
  }

  public Expression getInitializer() {
    return initializer;
  }
}