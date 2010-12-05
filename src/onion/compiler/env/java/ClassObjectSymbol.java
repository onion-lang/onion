/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import onion.compiler.IxCode;
import onion.compiler.OnionTypeBridge;
import onion.compiler.env.ClassTable;
import onion.lang.syntax.Modifier;

import org.apache.bcel.Constants;

/**
 * @author Kota Mizushima
 * Date: 2006/1/10
 */
public class ClassObjectSymbol extends IxCode.AbstractClassSymbol implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private Class klass;
  private ClassTable table;  
  private int modifier;
  private IxCode.MethodSymbol[] methods;
  private IxCode.FieldSymbol[] fields;
  private IxCode.ConstructorSymbol[] constructors;
  private OnionTypeBridge bridge;
  
  public ClassObjectSymbol(Class klass, ClassTable table) {
    this.klass = klass;
    this.table = table;
    this.bridge = new OnionTypeBridge(table);
    this.modifier = toOnionModifier(klass.getModifiers());
  }

  public boolean isInterface() {
    return (klass.getModifiers() & java.lang.reflect.Modifier.INTERFACE) != 0;
  }

  public int getModifier() {
    return modifier;
  }
  
  public String getName() {
    return klass.getName();
  }

  public IxCode.ClassSymbol getSuperClass() {
    Class superKlass = klass.getSuperclass();
    if(superKlass == null) return table.rootClass();
    IxCode.ClassSymbol superClass = table.load(superKlass.getName());
    if(superClass == this) return null;
    return superClass;
  }

  public IxCode.ClassSymbol[] getInterfaces() {
    Class[] interfaces = klass.getInterfaces();
    IxCode.ClassSymbol[] interfaceSyms = new IxCode.ClassSymbol[interfaces.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaceSyms[i] = table.load(interfaces[i].getName());
    }
    return interfaceSyms;
  }
  
  public IxCode.MethodSymbol[] getMethods() {
    if(methods == null){
      setMethods(klass.getMethods());
    }
    return (IxCode.MethodSymbol[]) methods.clone();
  }
  
  public IxCode.FieldSymbol[] getFields() {
    if(fields == null){
      setFields(klass.getFields());
    }
    return (IxCode.FieldSymbol[]) fields.clone();
  }
  
  public IxCode.ConstructorSymbol[] getConstructors() {
    if(constructors == null){
      setConstructors(klass.getConstructors());
    }
    return (IxCode.ConstructorSymbol[]) constructors.clone();
  }

  private static int toOnionModifier(int src){
    int modifier = 0;
    modifier |= (isOn(src, java.lang.reflect.Modifier.PRIVATE) ? Modifier.PRIVATE : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.PROTECTED) ? Modifier.PROTECTED : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.PUBLIC) ? Modifier.PUBLIC : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.STATIC) ? Modifier.STATIC : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.SYNCHRONIZED) ? Modifier.SYNCHRONIZED : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.ABSTRACT) ? Modifier.ABSTRACT : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.FINAL) ? Modifier.FINAL : modifier);
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
    this.methods = (IxCode.MethodSymbol[])symbols.toArray(new IxCode.MethodSymbol[0]);
  }
  
  private void setFields(Field[] fields){
    IxCode.FieldSymbol[] symbols = new IxCode.FieldSymbol[fields.length];
    for(int i = 0; i < fields.length; i++){
      symbols[i] = convertField(fields[i]);
    }
    this.fields = symbols;
  }
  
  private void setConstructors(Constructor[] methods){
    List symbols = new ArrayList();
    for(int i = 0; i < methods.length; i++){
      symbols.add(convertConstructor(methods[i]));
    }
    this.constructors = (IxCode.ConstructorSymbol[]) symbols.toArray(new IxCode.ConstructorSymbol[0]);
  }
  
  private IxCode.MethodSymbol convertMethod(Method method){
    Class[] arguments = method.getParameterTypes();
    IxCode.TypeRef[] argumentSymbols = new IxCode.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    IxCode.TypeRef returnSymbol = bridge.toOnionType(method.getReturnType());
    return new ClassFileMethodSymbol(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols, returnSymbol);
  }
  
  private IxCode.FieldSymbol convertField(Field field){
    IxCode.TypeRef symbol = bridge.toOnionType(field.getType());
    return new ClassFileFieldSymbol(
      toOnionModifier(field.getModifiers()), 
      this, field.getName(), symbol);
  }
  
  private IxCode.ConstructorSymbol convertConstructor(Constructor constructor){
    Class[] arguments = constructor.getParameterTypes();
    IxCode.TypeRef[] argumentSymbols = new IxCode.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorSymbol(
      toOnionModifier(constructor.getModifiers()),
      this, "<init>", argumentSymbols);
  }
}