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
public class ClassFileClassTypeRef extends IRT.AbstractClassTypeRef implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private JavaClass javaClass;
  private ClassTable table;  
  private int modifier;
  private MultiTable<IRT.MethodRef> methods;
  private OrderedTable<IRT.FieldRef> fields;
  private List<IRT.ConstructorRef> constructors;
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

  public IRT.ClassTypeRef superClass() {
    IRT.ClassTypeRef superClass = table.load(javaClass.getSuperclassName());
    if(superClass == this){
      return null;
    }
    return superClass;
  }

  public IRT.ClassTypeRef[] interfaces() {
    String[] interfaceNames = javaClass.getInterfaceNames();
    IRT.ClassTypeRef[] interfaces = new IRT.ClassTypeRef[interfaceNames.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaces[i] = table.load(interfaceNames[i]);
    }
    return interfaces;
  }
  
  public IRT.MethodRef[] methods() {
    requireMethodTable();
    return methods.values().toArray(new IRT.MethodRef[0]);
  }

  public IRT.MethodRef[] methods(String name) {
    requireMethodTable();
    return methods.get(name).toArray(new IRT.MethodRef[0]);
  }

  private void requireMethodTable() {
    if(methods == null){
      methods = new MultiTable<IRT.MethodRef>();
      for(Method method:javaClass.getMethods()){
        if(!method.getName().equals(CONSTRUCTOR_NAME)) methods.add(translate(method));
      }
    }
  }

  public IRT.FieldRef[] fields() {
    requireFieldTable();
    return fields.values().toArray(new IRT.FieldRef[0]);
  }

  public IRT.FieldRef field(String name) {
    requireFieldTable();
    return fields.get(name);
  }

  private void requireFieldTable() {
    if(fields == null){
      fields = new OrderedTable<IRT.FieldRef>();
      for(Field field:javaClass.getFields()) {
        fields.add(translate(field));
      }
    }
  }
  
  public IRT.ConstructorRef[] constructors() {
    if(constructors == null){
      constructors = new ArrayList<IRT.ConstructorRef>();
      for(Method method:javaClass.getMethods()) {
        if(method.getName().equals(CONSTRUCTOR_NAME)) {
          constructors.add(translateConstructor(method));
        }
      }
    }
    return constructors.toArray(new IRT.ConstructorRef[0]);
  }

  private static int toOnionModifier(int src){
    int modifier = 0;
    modifier |= (isOn(src, ACC_PRIVATE) ? Modifier.PRIVATE() : modifier);
    modifier |= (isOn(src, ACC_PROTECTED) ? Modifier.PROTECTED() : modifier);
    modifier |= (isOn(src, ACC_PUBLIC) ? Modifier.PUBLIC() : modifier);
    modifier |= (isOn(src, ACC_STATIC) ? Modifier.STATIC() : modifier);
    modifier |= (isOn(src, ACC_SYNCHRONIZED) ? Modifier.SYNCHRONIZED() : modifier);
    modifier |= (isOn(src, ACC_ABSTRACT) ? Modifier.ABSTRACT() : modifier);
    modifier |= (isOn(src, ACC_FINAL) ? Modifier.FINAL() : modifier);
    return modifier;
  }
  
  private static boolean isOn(int modifier, int flag){
    return (modifier & flag) != 0;
  }
  
  private IRT.MethodRef translate(Method method){
    Type[] arguments = method.getArgumentTypes();
    IRT.TypeRef[] argumentSymbols = new IRT.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    IRT.TypeRef returnSymbol = bridge.toOnionType(method.getReturnType());
    return new ClassFileMethodRef(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols, returnSymbol);
  }
  
  private IRT.FieldRef translate(Field field){
    IRT.TypeRef symbol = bridge.toOnionType(field.getType());
    return new ClassFileFieldRef(
      toOnionModifier(field.getModifiers()), 
      this, field.getName(), symbol);
  }
  
  private IRT.ConstructorRef translateConstructor(Method method){
    Type[] arguments = method.getArgumentTypes();
    IRT.TypeRef[] argumentSymbols = new IRT.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentSymbols[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorRef(
      toOnionModifier(method.getModifiers()),
      this, method.getName(), argumentSymbols);
  }
}