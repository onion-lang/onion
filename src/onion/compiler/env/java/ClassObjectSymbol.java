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

import onion.compiler.OnionTypeBridge;
import onion.compiler.env.ClassTable;

import org.apache.bcel.Constants;
import org.onion_lang.onion.lang.core.type.AbstractClassSymbol;
import org.onion_lang.onion.lang.core.type.ClassSymbol;
import org.onion_lang.onion.lang.core.type.ConstructorSymbol;
import org.onion_lang.onion.lang.core.type.FieldSymbol;
import org.onion_lang.onion.lang.core.type.MethodSymbol;
import org.onion_lang.onion.lang.core.type.TypeRef;
import org.onion_lang.onion.lang.syntax.Modifier;

/**
 * @author Kota Mizushima
 * Date: 2006/1/10
 */
public class ClassObjectSymbol extends AbstractClassSymbol implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private Class klass;
  private ClassTable table;  
  private int modifier;
  private MethodSymbol[] methods;
  private FieldSymbol[] fields;
  private ConstructorSymbol[] constructors;  
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

  public ClassSymbol getSuperClass() {
    Class superKlass = klass.getSuperclass();
    if(superKlass == null) return table.rootClass();
    ClassSymbol superClass = table.load(superKlass.getName());
    if(superClass == this) return null;
    return superClass;
  }

  public ClassSymbol[] getInterfaces() {
    Class[] interfaces = klass.getInterfaces();
    ClassSymbol[] interfaceSyms = new ClassSymbol[interfaces.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaceSyms[i] = table.load(interfaces[i].getName());
    }
    return interfaceSyms;
  }
  
  public MethodSymbol[] getMethods() {
    if(methods == null){
      setMethods(klass.getMethods());
    }
    return (MethodSymbol[]) methods.clone();
  }
  
  public FieldSymbol[] getFields() {
    if(fields == null){
      setFields(klass.getFields());
    }
    return (FieldSymbol[]) fields.clone();
  }
  
  public ConstructorSymbol[] getConstructors() {
    if(constructors == null){
      setConstructors(klass.getConstructors());
    }
    return (ConstructorSymbol[]) constructors.clone();
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
    this.methods = (MethodSymbol[])symbols.toArray(new MethodSymbol[0]);
  }
  
  private void setFields(Field[] fields){
    FieldSymbol[] symbols = new FieldSymbol[fields.length];
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
    this.constructors = (ConstructorSymbol[]) symbols.toArray(new ConstructorSymbol[0]);
  }
  
  private MethodSymbol convertMethod(Method method){
    Class[] arguments = method.getParameterTypes();
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
  
  private ConstructorSymbol convertConstructor(Constructor constructor){
    Class[] arguments = constructor.getParameterTypes();
    TypeRef[] argumentSymbols = new TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorSymbol(
      toOnionModifier(constructor.getModifiers()),
      this, "<init>", argumentSymbols);
  }
}