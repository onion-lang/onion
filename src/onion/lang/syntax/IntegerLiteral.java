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
public class IntegerLiteral extends Literal {
  private final int value;

  /**
   * @param location
   * @param value
   */
  public IntegerLiteral(Location location, int value) {
    this.value = value;
    setLocation(location);
  }

  /**
   * @return Returns the value.
   */
  public int getValue() {
    return value;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}