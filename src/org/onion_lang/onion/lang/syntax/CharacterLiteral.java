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
public class CharacterLiteral extends Literal {
  private final char value;

  /**
   * @param location
   * @param value
   */
  public CharacterLiteral(Location location, char value) {
    this.value = value;
    setLocation(location);
  }

  /**
   * @return Returns the value.
   */
  public char getValue() {
    return value;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}