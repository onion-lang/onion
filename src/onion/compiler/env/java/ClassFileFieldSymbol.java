/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/06/27
 */
public class ClassFileFieldSymbol implements FieldSymbol {
  private int modifier;
  private ClassSymbol classType;
  private String name;
  private TypeRef type;

  public ClassFileFieldSymbol(
    int modifier, ClassSymbol classType, String name, TypeRef type) {
    this.modifier = modifier;
    this.classType = classType;
    this.name = name;
    this.type = type;
  }

  public int getModifier() {
    return modifier;
  }

  public ClassSymbol getClassType() {
    return classType;
  }

  public String getName() {
    return name;
  }

  public TypeRef getType() {
    return type;
  }
}
