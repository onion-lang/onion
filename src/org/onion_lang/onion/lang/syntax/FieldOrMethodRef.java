/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

/**
 * @author Kota Mizushima
 *  
 */
public class FieldOrMethodRef extends Expression {  
  private final Expression target;
  private final String name;

  public FieldOrMethodRef(Expression target, String name) {
    this.target = target;
    this.name = name;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  public Expression getTarget() {
    return target;
  }

  public String getName() {
    return name;
  }
}