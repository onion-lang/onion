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