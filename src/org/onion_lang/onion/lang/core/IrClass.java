/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import java.util.*;

import org.onion_lang.onion.lang.core.type.*;

/**
 * This class represents class or interface definitions of internal language.
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public class IrClass extends AbstractClassSymbol implements IrNode {
  private boolean isInterface;
  private int modifier;
  private String name;
  private ClassSymbol superClass;
  private ClassSymbol[] interfaces;
  private List fields = new ArrayList();
  private List methods = new ArrayList();
  private List constructors = new ArrayList();
  private boolean isResolutionComplete;
  private boolean hasCyclicity;
  private String sourceFile;
  
  /**
   * 
   * @param isInterface indicates whether this class is interface or class
   * @param modifier class modifier
   * @param name class name. it cannot be null
   * @param superClass super class
   * @param interfaces super interfaces
   */
  public IrClass(boolean isInterface, int modifier, String name, ClassSymbol superClass, ClassSymbol[] interfaces) {
    this.isInterface = isInterface;
    this.modifier = modifier;
    this.name = name;
    this.superClass = superClass;
    this.interfaces = interfaces;
  }
  
  /**
   * This method creates interface definition.
   * @param modifier
   * @param name
   * @param interfaces
   * @return
   */
  public static IrClass newInterface(int modifier, String name, ClassSymbol[] interfaces){
    return new IrClass(true, modifier, name, null, interfaces);
  }
  
  /**
   * This method creates class definition.
   * @param modifier
   * @param name
   * @param superClass
   * @param interfaces
   * @return
   */
  public static IrClass newClass(int modifier, String name, ClassSymbol superClass, ClassSymbol[] interfaces){
    return new IrClass(false, modifier, name, superClass, interfaces);
  }
  
  /**
   * This method creates class definition.
   * @param modifier
   * @param name
   * @param superClass
   * @param interfaces
   * @return
   */
  public static IrClass newClass(int modifier, String name) {
    return new IrClass(false, modifier, name, null, null);
  }
  
  public boolean isInterface() {
    return isInterface;
  }
  
  public int getModifier() {
    return modifier;
  }
  
  public void setModifier(int modifier) {
    this.modifier = modifier;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  public String getName() {
    return name;
  }
  
  public void setSuperClass(ClassSymbol superClass) {
    this.superClass = superClass;
  }
  
  public ClassSymbol getSuperClass() {
    return superClass;
  }
  
  public void setInterfaces(ClassSymbol[] interfaces) {
    this.interfaces = interfaces;
  }

  public ClassSymbol[] getInterfaces() {
    return interfaces;
  }
  
  public void setResolutionComplete(boolean isInResolution) {
    this.isResolutionComplete = isInResolution;
  }
  
  public boolean isResolutionComplete() {
    return isResolutionComplete;
  }
  
  public void addMethod(MethodSymbol method) {
    methods.add(method);
  }
  
  public void addField(FieldSymbol field) {
    fields.add(field);
  }
  
  public void addConstructor(ConstructorSymbol constructor) {
    constructors.add(constructor);
  }
  
  public void addDefaultConstructor() {
    constructors.add(IrConstructor.newDefaultConstructor(this));
  }
  
  public MethodSymbol[] getMethods() {
    return ((MethodSymbol[])methods.toArray(new MethodSymbol[0]));
  }

  public FieldSymbol[] getFields() {
    return ((FieldSymbol[])fields.toArray(new FieldSymbol[0]));
  }
  
  public ConstructorSymbol[] getConstructors() {
    return ((ConstructorSymbol[]) constructors.toArray(new ConstructorSymbol[0]));
  }
  
  public void setSourceFile(String sourceFile) {
    this.sourceFile = sourceFile;
  }
  
  public String getSourceFile(){
    return sourceFile;
  }  
}
