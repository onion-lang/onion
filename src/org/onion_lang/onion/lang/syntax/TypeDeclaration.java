/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.syntax;

/**
 * Represents type declaration which subclass is class declaration or
 * interafce declaration.
 * @author Kota Mizushima
 *  
 */
public abstract class TypeDeclaration extends TopLevelElement {
  private final int modifier;
  private final String name;
  
  /**
   * 
   * @param modifiers
   * @param name
   */
  public TypeDeclaration(int modifier, String name) {
    this.modifier = modifier;
    this.name = name;
  }
  
  public int getModifier(){
    return modifier;
  }
  
  public String getName() {
    return name;
  }
}