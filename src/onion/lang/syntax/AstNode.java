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
 * @author mizu
 */
public abstract class AstNode {
  private Location location; 
  
  public AstNode() {
  }
  
  public AstNode(Location location) {
    this.location = location;
  }
  
  public abstract Object accept(ASTVisitor visitor, Object context);

  public Location getLocation() {
    return location;
  }
  
  public void setLocation(Location location) {
    this.location = location;
  }
}