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
public class StaticIDExpression extends Expression {  
  private final TypeSpec type;
  private final String name;

  public StaticIDExpression(Location loc, TypeSpec type, String name) {
    this.type = type;
    this.name = name;
    setLocation(loc);
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

  /**
   * @return type
   */
  public TypeSpec getType() {
    return type;
  }

  /**
   * @return name
   */
  public String getName() {
    return name;
  }
}