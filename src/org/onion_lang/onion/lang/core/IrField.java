/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrField implements IrNode, FieldSymbol {

  private int modifier;
  private ClassSymbol classType;
  private String name;
  private TypeRef type;
  
  public IrField(
    int modifier, ClassSymbol classType, String name, TypeRef type) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.type = type;
  }

  public ClassSymbol getClassType() {
    return classType;
  }
  
  public int getModifier() {
    return modifier;
  }
  
  public String getName() {
    return name;
  }
  
  public TypeRef getType() {
    return type;
  }
  
}
