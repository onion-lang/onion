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
public class ClassFileFieldRef implements IxCode.FieldRef {
  private int modifier;
  private IxCode.ClassTypeRef classType;
  private String name;
  private IxCode.TypeRef type;

  public ClassFileFieldRef(
    int modifier, IxCode.ClassTypeRef classType, String name, IxCode.TypeRef type) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.type = type;
  }

  public int getModifier() {
    return modifier;
  }

  public IxCode.ClassTypeRef getClassType() {
    return classType;
  }

  public String getName() {
    return name;
  }

  public IxCode.TypeRef getType() {
    return type;
  }
}
