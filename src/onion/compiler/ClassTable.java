/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.util.*;

import onion.compiler.env.java.ClassFileClassTypeRef;
import onion.compiler.env.java.ClassFileTable;
import onion.compiler.env.java.ReflectionalClassTypeRef;
import onion.compiler.util.Strings;

import org.apache.bcel.classfile.JavaClass;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassTable {
  private OrderedTable<IRT.ClassDefinition> classes;
  private Map<String, IRT.ClassTypeRef> classFiles;
  private Map<String, IRT.ArrayTypeRef> arrayClasses;
  private ClassFileTable table;
  
  public ClassTable(String classPath) {
    classes = new OrderedTable<IRT.ClassDefinition>();
    classFiles = new HashMap<String, IRT.ClassTypeRef>();
    arrayClasses = new HashMap<String, IRT.ArrayTypeRef>();
    table = new ClassFileTable(classPath);
  }
  
  public OrderedTable<IRT.ClassDefinition> classes() {
    return classes;
  }
  
  public IRT.ArrayTypeRef loadArray(IRT.TypeRef component, int dimension){
    String arrayName = Strings.repeat("[", dimension) + component.name();
    IRT.ArrayTypeRef array = arrayClasses.get(arrayName);
    if(array != null) return array;
    array = new IRT.ArrayTypeRef(component, dimension, this);
    arrayClasses.put(arrayName, array);
    return array;
  }
  
  public IRT.ClassTypeRef load(String className){
    IRT.ClassTypeRef clazz = lookup(className);
    if(clazz == null){
      JavaClass javaClass = table.load(className);
      if(javaClass != null){
        clazz = new ClassFileClassTypeRef(javaClass, this);
        classFiles.put(clazz.name(), clazz);
      }else{
        try {
          clazz = new ReflectionalClassTypeRef(Class.forName(className, true, Thread.currentThread().getContextClassLoader()), this);
          classFiles.put(clazz.name(), clazz);
        }catch(ClassNotFoundException e){
        }
      }
    }
    return clazz;
  }
  
  public IRT.ClassTypeRef rootClass(){
    return load("java.lang.Object");
  }
  
  public IRT.ClassTypeRef lookup(String className){
    IRT.ClassTypeRef ref = classes.get(className);
    return (ref != null) ? ref : classFiles.get(className);
  }

}