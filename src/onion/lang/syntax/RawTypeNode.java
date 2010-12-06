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
 * Date: 2005/04/10
 */
public class RawTypeNode extends TypeNode {
  public static final int BASIC = 0;  
  public static final int NOT_QUALIFIED = 1;  
  public static final int QUALIFIED = 2;
  
  private final String name;
  private final int kind;

  public RawTypeNode(Location loc, String name, int kind) {
    super(loc);
    this.name = name;
    this.kind = kind;
  }
  
  public RawTypeNode(Location loc, String name) {
    this(loc, name, QUALIFIED);
  }
  
  public String name(){
    return name;
  }

  public int kind(){
    return kind;
  }
  
  public Object accept(ASTVisitor visitor, Object context){
    return visitor.visit(this, context);
  }
}