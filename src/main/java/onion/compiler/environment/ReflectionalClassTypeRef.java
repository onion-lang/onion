/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import onion.compiler.*;
import onion.compiler.Modifier;

/**
 * @author Kota Mizushima
 * Date: 2006/1/10
 */
public class ReflectionalClassTypeRef extends IRT.AbstractClassTypeRef {
  private static final String CONSTRUCTOR_NAME = "<init>";
  private Class klass;
  private ClassTable table;
  private int modifier;
  private MultiTable<IRT.MethodRef> methods;
  private OrderedTable<IRT.FieldRef> fields;
  private List<IRT.ConstructorRef> constructors;
  private OnionTypeBridge bridge;
  
  public ReflectionalClassTypeRef(Class klass, ClassTable table) {
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

  public IRT.ClassTypeRef superClass() {
    Class superKlass = klass.getSuperclass();
    if(superKlass == null) return table.rootClass();
    IRT.ClassTypeRef superClass = table.load(superKlass.getName());
    if(superClass == this) return null;
    return superClass;
  }

  public IRT.ClassTypeRef[] interfaces() {
    Class[] interfaces = klass.getInterfaces();
    IRT.ClassTypeRef[] interfaceSyms = new IRT.ClassTypeRef[interfaces.length];
    for(int i = 0; i < interfaces.length; i++){
      interfaceSyms[i] = table.load(interfaces[i].getName());
    }
    return interfaceSyms;
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
      for(Method method:klass.getMethods()){
        if(!method.getName().equals(CONSTRUCTOR_NAME)){
          methods.add(translate(method));
        }
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
      for(Field field:klass.getFields()) {
        fields.add(translate(field));
      }
    }
  }

  public IRT.ConstructorRef[] constructors() {
    if(constructors == null){
      constructors = new ArrayList<IRT.ConstructorRef>();
      for(Constructor method:klass.getConstructors()) {
        constructors.add(translate(method));
      }
    }
    return constructors.toArray(new IRT.ConstructorRef[0]);
  }

  private static int toOnionModifier(int src){
    int modifier = 0;
    modifier |= (isOn(src, java.lang.reflect.Modifier.PRIVATE) ? Modifier.PRIVATE() : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.PROTECTED) ? Modifier.PROTECTED() : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.PUBLIC) ? Modifier.PUBLIC() : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.STATIC) ? Modifier.STATIC() : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.SYNCHRONIZED) ? Modifier.SYNCHRONIZED() : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.ABSTRACT) ? Modifier.ABSTRACT() : modifier);
    modifier |= (isOn(src, java.lang.reflect.Modifier.FINAL) ? Modifier.FINAL() : modifier);
    return modifier;
  }
  
  private static boolean isOn(int modifier, int flag){
    return (modifier & flag) != 0;
  }

  private IRT.MethodRef translate(Method method){
    Class[] arguments = method.getParameterTypes();
    IRT.TypeRef[] argumentRefs = new IRT.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentRefs[i] = bridge.toOnionType(arguments[i]);
    }
    IRT.TypeRef returnRef = bridge.toOnionType(method.getReturnType());
    return new ClassFileMethodRef(toOnionModifier(method.getModifiers()), this, method.getName(), argumentRefs, returnRef);
  }
  
  private IRT.FieldRef translate(Field field){
    return new ClassFileFieldRef(toOnionModifier(field.getModifiers()),  this, field.getName(), bridge.toOnionType(field.getType()));
  }
  
  private IRT.ConstructorRef translate(Constructor constructor){
    Class[] arguments = constructor.getParameterTypes();
    IRT.TypeRef[] argumentRefs = new IRT.TypeRef[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentRefs[i] = bridge.toOnionType(arguments[i]);
    }
    return new ClassFileConstructorRef(toOnionModifier(constructor.getModifiers()), this, "<init>", argumentRefs);
  }
}