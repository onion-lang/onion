/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment;

import onion.compiler.IRT;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileFieldRef implements IRT.FieldRef {
  private int modifier;
  private IRT.ClassTypeRef affiliation;
  private String name;
  private IRT.TypeRef type;

  public ClassFileFieldRef(int modifier, IRT.ClassTypeRef affiliation, String name, IRT.TypeRef type) {
    this.modifier = modifier;
    this.affiliation = affiliation;
    this.name = name;
    this.type = type;
  }

  public int modifier() {
    return modifier;
  }

  public IRT.ClassTypeRef affiliation() {
    return affiliation;
  }

  public String name() {
    return name;
  }

  public IRT.TypeRef type() {
    return type;
  }
}
