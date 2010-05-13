/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.env.java;

import java.util.ArrayList;
import java.util.List;

import onion.compiler.OnionTypeBridge;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.Type;
import org.onion_lang.onion.compiler.env.*;
import org.onion_lang.onion.lang.core.type.*;
import org.onion_lang.onion.lang.syntax.Modifier;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassFileSymbol extends AbstractClassSymbol implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private JavaClass javaClass;
  private ClassTable table;  
  private int modifier;
  private MethodSymbol[] methods;
  private FieldSymbol[] fields;
  private ConstructorSymbol[] constructors;  
  private OnionTypeBridge bridge;
  
  public ClassFileSymbol(JavaClass javaClass, ClassTable table) {
    this.javaClass = javaClass;
    this.table = table;
    this.bridge = new OnionTypeBridge(table);
    this.modifier = toOnionModifier(javaClass.getModifiers());
  }

  public boolean isInterface() {
    return (javaClass.getModifiers() & Constants.ACC_INTERFACE) != 0;
  }

  public int getModifier() {
    return modifier;
  }
  
  public String getName() {
    return javaClass.getClassName();
  }

  public ClassSymbol getSuperClass() {
    ClassSymbol superClass = table.load(javaClass.getSuperclassName());
    if(superClass == this){
      return null;
    }
    return superClass;
  }

  public ClassSymbol[] getInterfaces() {
    String[] interfaceNames = javaClass.getInterfaceNames();
    ClassSymbol[] interfaces = new ClassSymbol[interfaceNames.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaces[i] = table.load(interfaceNames[i]);
    }
    return interfaces;
  }
  
  public MethodSymbol[] getMethods() {
    if(methods == null){
      setMethods(javaClass.getMethods());
    }
    return (MethodSymbol[]) methods.clone();
  }
  
  public FieldSymbol[] getFields() {
    if(fields == null){
      setFields(javaClass.getFields());
    }
    return (FieldSymbol[]) fields.clone();
  }
  
  public ConstructorSymbol[] getConstructors() {
    if(constructors == null){
      setConstructors(javaClass.getMethods());
    }
    return (ConstructorSymbol[]) constructors.clone();
  }

  private static int toOnionModifier(int src){
    int modifier = 0;
    modifier |= (isOn(src, ACC_PRIVATE) ? Modifier.PRIVATE : modifier);
    modifier |= (isOn(src, ACC_PROTECTED) ? Modifier.PROTECTED : modifier);
    modifier |= (isOn(src, ACC_PUBLIC) ? Modifier.PUBLIC : modifier);
    modifier |= (isOn(src, ACC_STATIC) ? Modifier.STATIC : modifier);
    modifier |= (isOn(src, ACC_SYNCHRONIZED) ? Modifier.SYNCHRONIZED : modifier);
    modifier |= (isOn(src, ACC_ABSTRACT) ? Modifier.ABSTRACT : modifier);
    modifier |= (isOn(src, ACC_FINAL) ? Modifier.FINAL : modifier);
    return modifier;
  }
  
  private static boolean isOn(int modifier, int flag){
    return (modifier & flag) != 0;
  }
  
  private void setMethods(Method[] methods){
    List symbols = new ArrayList();
    for(int i = 0; i < methods.length; i++){
      if(!methods[i].getName().equals(CONSTRUCTOR_NAME)){
        symbols.add(convertMethod(methods[i]));
      }
    }
    this.methods = (MethodSymbol[])symbols.toArray(new MethodSymbol[0]);
  }
  
  private void setFields(Field[] fields){
    FieldSymbol[] symbols = new FieldSymbol[fields.length];
    for(int i = 0; i < fields.length; i++){
      symbols[i] = convertField(fields[i]);
    }
    this.fields = symbols;
  }
  
  private void setConstructors(Method[] methods){
    List symbols = new ArrayList();
    for(int i = 0; i < methods.length; i++){
      if(methods[i].getName().equals(CONSTRUCTOR_NAME)){
        symbols.add(convertConstructor(methods[i]));
      }
    }
    this.constructors = 
      (ConstructorSymbol[]) symbols.toArray(new ConstructorSymbol[0]);
  }
  
  private MethodSymbol convertMethod(Method method){
    Type[] arguments = method.getArgumentTypes();
    TypeRef[] argumentSymbols = new TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    TypeRef returnSymbol = bridge.toOnionType(method.getReturnType());
    return new ClassFileMethodSymbol(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols, returnSymbol);
  }
  
  private FieldSymbol convertField(Field field){
    TypeRef symbol = bridge.toOnionType(field.getType());
    return new ClassFileFieldSymbol(
      toOnionModifier(field.getModifiers()), 
      this, field.getName(), symbol);
  }
  
  private ConstructorSymbol convertConstructor(Method method){
    Type[] arguments = method.getArgumentTypes();
    TypeRef[] argumentSymbols = new TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorSymbol(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols);
  }
}