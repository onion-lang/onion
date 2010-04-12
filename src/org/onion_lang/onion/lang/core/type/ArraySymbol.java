/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core.type;

import org.onion_lang.onion.compiler.env.*;
import org.onion_lang.onion.compiler.util.Strings;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class ArraySymbol extends AbstractObjectSymbol {
  private ClassTable table;
  private TypeRef component;
  private int dimension;
  private ClassSymbol superClass;
  private ClassSymbol[] interfaces;
  private String name;
  
  public ArraySymbol(TypeRef component, int dimension, ClassTable table){
    this.component = component;
    this.dimension = dimension;
    this.superClass = table.load("java.lang.Object");
    this.interfaces = new ClassSymbol[]{
      table.load("java.io.Serializable"),
      table.load("java.lang.Cloneable")
    };
    this.name = Strings.repeat("[", dimension) + component.getName();
  }
  
  public TypeRef getComponent(){
    return component;
  }
  
  public TypeRef getBase(){
    if(dimension == 1){
      return component;
    }else{
      return table.loadArray(component, dimension - 1);
    }
  }
  
  public int getDimension(){
    return dimension;
  }

  public boolean isInterface() {
    return false;
  }

  public int getModifier() {
    return 0;
  }

  public ClassSymbol getSuperClass() {
    return superClass;
  }

  public ClassSymbol[] getInterfaces() {
    return interfaces;
  }

  public MethodSymbol[] getMethods() {
    return superClass.getMethods();
  }

  public FieldSymbol[] getFields() {
    return superClass.getFields();
  }

  public String getName() {
    return name;
  }
  
  public boolean isArrayType() {
    return true;
  }
  
  public boolean isClassType() {
    return false;
  }
}