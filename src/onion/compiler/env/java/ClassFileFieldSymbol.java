/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import onion.compiler.IxCode;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileFieldSymbol implements IxCode.FieldSymbol {
  private int modifier;
  private IxCode.ClassSymbol classType;
  private String name;
  private IxCode.TypeRef type;

  public ClassFileFieldSymbol(
    int modifier, IxCode.ClassSymbol classType, String name, IxCode.TypeRef type) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.type = type;
  }

  public int getModifier() {
    return modifier;
  }

  public IxCode.ClassSymbol getClassType() {
    return classType;
  }

  public String getName() {
    return name;
  }

  public IxCode.TypeRef getType() {
    return type;
  }
}
