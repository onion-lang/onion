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
package org.onion_lang.onion.lang.syntax;


import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

;


/**
 * @author Kota Mizushima
 *  
 */
public class LongLiteral extends Literal {
  private final long value;

  /**
   * @param location
   * @param value
   */
  public LongLiteral(Location location, long value) {
    this.value = value;
    setLocation(location);
  }

  /**
   * @return Returns the value.
   */
  public long getValue() {
    return value;
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}