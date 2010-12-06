/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import java.util.*;

import onion.compiler.IxCode;
import onion.compiler.env.java.ClassFileTypeRef;
import onion.compiler.env.java.ClassFileTable;
import onion.compiler.env.java.ClassObjectTypeRef;
import onion.compiler.util.Strings;

import org.apache.bcel.classfile.JavaClass;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassTable {
  private LinkedHashMap<String, IxCode.ClassDefinition> classes;
  private Map<String, IxCode.ClassTypeRef> classFiles;
  private Map<String, IxCode.ArrayTypeRef> arrayClasses;
  private ClassFileTable table;
  
  public ClassTable(String classPath) {
    classes = new LinkedHashMap<String, IxCode.ClassDefinition>();
    classFiles = new HashMap<String, IxCode.ClassTypeRef>();
    arrayClasses = new HashMap<String, IxCode.ArrayTypeRef>();
    table = new ClassFileTable(classPath);
  }
  
  public void addSourceClass(IxCode.ClassDefinition node){
    classes.put(node.getName(), node);
  }
  
  public IxCode.ClassDefinition[] getSourceClasses(){
    return classes.values().toArray(new IxCode.ClassDefinition[0]);
  }
  
  public IxCode.ArrayTypeRef loadArray(IxCode.TypeRef component, int dimension){
    String arrayName = Strings.repeat("[", dimension) + component.getName();
    IxCode.ArrayTypeRef array = (IxCode.ArrayTypeRef) arrayClasses.get(arrayName);
    if(array != null) return array;
    array = new IxCode.ArrayTypeRef(component, dimension, this);
    arrayClasses.put(arrayName, array);
    return array;
  }
  
  public IxCode.ClassTypeRef load(String className){
    IxCode.ClassTypeRef clazz = lookup(className);
    if(clazz == null){
      JavaClass javaClass = table.load(className);
      if(javaClass != null){
        clazz = new ClassFileTypeRef(javaClass, this);
        classFiles.put(clazz.getName(), clazz);
      }else{
        try {
          clazz = new ClassObjectTypeRef(Class.forName(className, true, Thread.currentThread().getContextClassLoader()), this);
          classFiles.put(clazz.getName(), clazz);
        }catch(ClassNotFoundException e){
        }
      }
    }
    return clazz;
  }
  
  public IxCode.ClassTypeRef rootClass(){
    return load("java.lang.Object");
  }
  
  public IxCode.ClassTypeRef lookup(String className){
    IxCode.ClassTypeRef ref = classes.get(className);
    return (ref != null) ? ref : classFiles.get(className);
  }

}