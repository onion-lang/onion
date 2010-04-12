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
 * Date: 2005/04/09
 */
public class TypeArgument extends AstNode {
  private final TypeSpec[] bounds;
  
  public TypeArgument(TypeSpec[] bounds) {
    this.bounds = bounds;
  }
  
  public TypeSpec getBound(int index) {
    return bounds[index];
  }
  
  public int size() {
    return bounds.length;
  }
  
  public boolean hasBounds() {
    return bounds.length > 0;
  }
  
  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}
