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

import onion.compiler.ClassTable;
import onion.compiler.IxCode;
import onion.compiler.OnionTypeBridge;
import onion.lang.syntax.Modifier;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.Type;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassFileTypeRef extends IxCode.AbstractClassTypeRef implements Constants{
  private static final String CONSTRUCTOR_NAME = "<init>";
  private JavaClass javaClass;
  private ClassTable table;  
  private int modifier;
  private IxCode.MethodRef[] methods;
  private IxCode.FieldRef[] fields;
  private IxCode.ConstructorRef[] constructors;
  private OnionTypeBridge bridge;
  
  public ClassFileTypeRef(JavaClass javaClass, ClassTable table) {
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
  
  public String name() {
    return javaClass.getClassName();
  }

  public IxCode.ClassTypeRef getSuperClass() {
    IxCode.ClassTypeRef superClass = table.load(javaClass.getSuperclassName());
    if(superClass == this){
      return null;
    }
    return superClass;
  }

  public IxCode.ClassTypeRef[] getInterfaces() {
    String[] interfaceNames = javaClass.getInterfaceNames();
    IxCode.ClassTypeRef[] interfaces = new IxCode.ClassTypeRef[interfaceNames.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaces[i] = table.load(interfaceNames[i]);
    }
    return interfaces;
  }
  
  public IxCode.MethodRef[] getMethods() {
    if(methods == null){
      setMethods(javaClass.getMethods());
    }
    return (IxCode.MethodRef[]) methods.clone();
  }
  
  public IxCode.FieldRef[] getFields() {
    if(fields == null){
      setFields(javaClass.getFields());
    }
    return (IxCode.FieldRef[]) fields.clone();
  }
  
  public IxCode.ConstructorRef[] getConstructors() {
    if(constructors == null){
      setConstructors(javaClass.getMethods());
    }
    return (IxCode.ConstructorRef[]) constructors.clone();
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
    this.methods = (IxCode.MethodRef[])symbols.toArray(new IxCode.MethodRef[0]);
  }
  
  private void setFields(Field[] fields){
    IxCode.FieldRef[] refs = new IxCode.FieldRef[fields.length];
    for(int i = 0; i < fields.length; i++){
      refs[i] = convertField(fields[i]);
    }
    this.fields = refs;
  }
  
  private void setConstructors(Method[] methods){
    List symbols = new ArrayList();
    for(int i = 0; i < methods.length; i++){
      if(methods[i].getName().equals(CONSTRUCTOR_NAME)){
        symbols.add(convertConstructor(methods[i]));
      }
    }
    this.constructors = 
      (IxCode.ConstructorRef[]) symbols.toArray(new IxCode.ConstructorRef[0]);
  }
  
  private IxCode.MethodRef convertMethod(Method method){
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
  
  private IxCode.FieldRef convertField(Field field){
    IxCode.TypeRef symbol = bridge.toOnionType(field.getType());
    return new ClassFileFieldRef(
      toOnionModifier(field.getModifiers()), 
      this, field.getName(), symbol);
  }
  
  private IxCode.ConstructorRef convertConstructor(Method method){
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