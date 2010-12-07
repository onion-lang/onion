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
  private IxCode.ClassTypeRef affiliation;
  private String name;
  private IxCode.TypeRef type;

  public ClassFileFieldRef(int modifier, IxCode.ClassTypeRef affiliation, String name, IxCode.TypeRef type) {
    this.modifier = modifier;
    this.affiliation = affiliation;
    this.name = name;
    this.type = type;
  }

  public int modifier() {
    return modifier;
  }

  public IxCode.ClassTypeRef affiliation() {
    return affiliation;
  }

  public String name() {
    return name;
  }

  public IxCode.TypeRef type() {
    return type;
  }
}
