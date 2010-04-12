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
public class DoubleLiteral extends Literal {
  private final double value;

  /**
   * @param location 
   * @param value
   */
  public DoubleLiteral(Location location, double value) {
    this.value = value;
    setLocation(location);
  }

  /**
   * @return Returns the value.
   */
  public double getValue() {
    return value;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}