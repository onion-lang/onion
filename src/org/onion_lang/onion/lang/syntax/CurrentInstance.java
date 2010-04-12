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
public class CurrentInstance extends Expression {

  /**
   * @param location
   */
  public CurrentInstance(Location location) {
    setLocation(location);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }
}