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
public class BooleanLiteral extends Literal {
  private final boolean value;

  /**
   * @param location
   * @param value
   */
  public BooleanLiteral(Location location, boolean value) {
    this.value = value;
    setLocation(location);
  }

  /**
   * @return Returns the value.
   */
  public boolean getValue() {
    return value;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}