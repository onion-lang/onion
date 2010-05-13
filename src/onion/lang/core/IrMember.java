/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core;

import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrMember implements IrNode{  
  private final int modifier;
  private final TypeRef classType;

  public IrMember(int modifier, TypeRef classType){
    this.modifier = modifier;
    this.classType = classType;
  }

  public TypeRef getClassType() {
    return classType;
  }
  
  public int getModifier() {
    return modifier;
  }  
}