/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env.java;

import java.util.ArrayList;
import java.util.List;

import onion.compiler.*;
import onion.compiler.Modifier;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.Type;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassFileClassTypeRef extends IxCode.AbstractClassTypeRef implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private JavaClass javaClass;
  private ClassTable table;  
  private int modifier;
  private MultiTable<IxCode.MethodRef> methods;
  private OrderedTable<IxCode.FieldRef> fields;
  private List<IxCode.ConstructorRef> constructors;
  private OnionTypeBridge bridge;
  
  public ClassFileClassTypeRef(JavaClass javaClass, ClassTable table) {
    this.javaClass = javaClass;
    this.table = table;
    this.bridge = new OnionTypeBridge(table);
    this.modifier = toOnionModifier(javaClass.getModifiers());
  }

  public boolean isInterface() {
    return (javaClass.getModifiers() & Constants.ACC_INTERFACE) != 0;
  }

  public int modifier() {
    return modifier;
  }
  
  public String name() {
    return javaClass.getClassName();
  }

  public IxCode.ClassTypeRef superClass() {
    IxCode.ClassTypeRef superClass = table.load(javaClass.getSuperclassName());
    if(superClass == this){
      return null;
    }
    return superClass;
  }

  public IxCode.ClassTypeRef[] interfaces() {
    String[] interfaceNames = javaClass.getInterfaceNames();
    IxCode.ClassTypeRef[] interfaces = new IxCode.ClassTypeRef[interfaceNames.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaces[i] = table.load(interfaceNames[i]);
    }
    return interfaces;
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
      for(Method method:javaClass.getMethods()){
        if(!method.getName().equals(CONSTRUCTOR_NAME)) methods.add(translate(method));
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
      for(Field field:javaClass.getFields()) {
        fields.add(translate(field));
      }
    }
  }
  
  public IxCode.ConstructorRef[] constructors() {
    if(constructors == null){
      constructors = new ArrayList<IxCode.ConstructorRef>();
      for(Method method:javaClass.getMethods()) {
        if(method.getName().equals(CONSTRUCTOR_NAME)) {
          constructors.add(translateConstructor(method));
        }
      }
    }
    return constructors.toArray(new IxCode.ConstructorRef[0]);
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
  
  private IxCode.MethodRef translate(Method method){
    Type[] arguments = method.getArgumentTypes();
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
  
  private IxCode.ConstructorRef translateConstructor(Method method){
    Type[] arguments = method.getArgumentTypes();
    IxCode.TypeRef[] argumentSymbols = new IxCode.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorRef(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols);
  }
}