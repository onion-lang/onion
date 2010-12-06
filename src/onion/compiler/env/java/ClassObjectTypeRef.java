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

import onion.compiler.*;
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
  private MultiTable<IxCode.MethodRef> methods;
  private OrderedTable<IxCode.FieldRef> fields;
  private List<IxCode.ConstructorRef> constructors;
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

  public int modifier() {
    return modifier;
  }
  
  public String name() {
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
  
  public IxCode.MethodRef[] methods() {
    requireMethodTable();
    return methods.values().toArray(new IxCode.MethodRef[0]);
  }

  public IxCode.MethodRef[] methods(String name) {
    requireMethodTable();
    return methods.get(name).toArray(new IxCode.MethodRef[0]);
  }

  private void requireMethodTable() {
    if(methods == null){
      methods = new MultiTable<IxCode.MethodRef>();
      for(Method method:klass.getMethods()){
        if(!method.getName().equals(CONSTRUCTOR_NAME)){
          methods.add(translate(method));
        }
      }
    }
  }
  
  public IxCode.FieldRef[] fields() {
    requireFieldTable();
    return fields.values().toArray(new IxCode.FieldRef[0]);
  }

  public IxCode.FieldRef field(String name) {
    requireFieldTable();
    return fields.get(name);
  }

  private void requireFieldTable() {
    if(fields == null){
      fields = new OrderedTable<IxCode.FieldRef>();
      for(Field field:klass.getFields()) {
        fields.add(translate(field));
      }
    }
  }

  public IxCode.ConstructorRef[] constructors() {
    if(constructors == null){
      constructors = new ArrayList<IxCode.ConstructorRef>();
      for(Constructor method:klass.getConstructors()) {
        constructors.add(translate(method));
      }
    }
    return constructors.toArray(new IxCode.ConstructorRef[0]);
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

  private IxCode.MethodRef translate(Method method){
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
  
  private IxCode.FieldRef translate(Field field){
    IxCode.TypeRef symbol = bridge.toOnionType(field.getType());
    return new ClassFileFieldRef(
      toOnionModifier(field.getModifiers()), 
      this, field.getName(), symbol);
  }
  
  private IxCode.ConstructorRef translate(Constructor constructor){
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