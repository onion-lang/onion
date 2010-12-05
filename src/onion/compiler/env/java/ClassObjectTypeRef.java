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
public class ClassObjectTypeRef extends IxCode.AbstractClassTypeRef implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private Class klass;
  private ClassTable table;  
  private int modifier;
  private IxCode.MethodRef[] methods;
  private IxCode.FieldRef[] fields;
  private IxCode.ConstructorRef[] constructors;
  private OnionTypeBridge bridge;
  
  public ClassObjectTypeRef(Class klass, ClassTable table) {
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

  public IxCode.ClassTypeRef getSuperClass() {
    Class superKlass = klass.getSuperclass();
    if(superKlass == null) return table.rootClass();
    IxCode.ClassTypeRef superClass = table.load(superKlass.getName());
    if(superClass == this) return null;
    return superClass;
  }

  public IxCode.ClassTypeRef[] getInterfaces() {
    Class[] interfaces = klass.getInterfaces();
    IxCode.ClassTypeRef[] interfaceSyms = new IxCode.ClassTypeRef[interfaces.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaceSyms[i] = table.load(interfaces[i].getName());
    }
    return interfaceSyms;
  }
  
  public IxCode.MethodRef[] getMethods() {
    if(methods == null){
      setMethods(klass.getMethods());
    }
    return (IxCode.MethodRef[]) methods.clone();
  }
  
  public IxCode.FieldRef[] getFields() {
    if(fields == null){
      setFields(klass.getFields());
    }
    return (IxCode.FieldRef[]) fields.clone();
  }
  
  public IxCode.ConstructorRef[] getConstructors() {
    if(constructors == null){
      setConstructors(klass.getConstructors());
    }
    return (IxCode.ConstructorRef[]) constructors.clone();
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
    this.methods = (IxCode.MethodRef[])symbols.toArray(new IxCode.MethodRef[0]);
  }
  
  private void setFields(Field[] fields){
    IxCode.FieldRef[] refs = new IxCode.FieldRef[fields.length];
    for(int i = 0; i < fields.length; i++){
      refs[i] = convertField(fields[i]);
    }
    this.fields = refs;
  }
  
  private void setConstructors(Constructor[] methods){
    List symbols = new ArrayList();
    for(int i = 0; i < methods.length; i++){
      symbols.add(convertConstructor(methods[i]));
    }
    this.constructors = (IxCode.ConstructorRef[]) symbols.toArray(new IxCode.ConstructorRef[0]);
  }
  
  private IxCode.MethodRef convertMethod(Method method){
    Class[] arguments = method.getParameterTypes();
    IxCode.TypeRef[] argumentSymbols = new IxCode.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    IxCode.TypeRef returnSymbol = bridge.toOnionType(method.getReturnType());
    return new ClassFileMethodRef(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols, returnSymbol);
  }
  
  private IxCode.FieldRef convertField(Field field){
    IxCode.TypeRef symbol = bridge.toOnionType(field.getType());
    return new ClassFileFieldRef(
      toOnionModifier(field.getModifiers()), 
      this, field.getName(), symbol);
  }
  
  private IxCode.ConstructorRef convertConstructor(Constructor constructor){
    Class[] arguments = constructor.getParameterTypes();
    IxCode.TypeRef[] argumentSymbols = new IxCode.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorRef(
      toOnionModifier(constructor.getModifiers()),
      this, "<init>", argumentSymbols);
  }
}