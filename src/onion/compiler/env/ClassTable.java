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
import onion.compiler.env.java.ClassFileSymbol;
import onion.compiler.env.java.ClassFileTable;
import onion.compiler.env.java.ClassObjectSymbol;
import onion.compiler.util.Strings;

import org.apache.bcel.classfile.JavaClass;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassTable {
  private List<IxCode.ClassDefinition> sourceClasses;
  private Map<String, IxCode.ClassDefinition> sourceClassMap;
  private Map<String, IxCode.ClassSymbol> classFileMap;
  private Map<String, IxCode.ArraySymbol> arrayMap;
  private ClassFileTable table;
  
  public ClassTable(String classPath) {
    sourceClasses  = new ArrayList<IxCode.ClassDefinition>();
    sourceClassMap = new HashMap<String, IxCode.ClassDefinition>();
    classFileMap   = new HashMap<String, IxCode.ClassSymbol>();
    arrayMap       = new HashMap<String, IxCode.ArraySymbol>();
    table          = new ClassFileTable(classPath);
  }
  
  public void addSourceClass(IxCode.ClassDefinition node){
    sourceClasses.add(node);
    sourceClassMap.put(node.getName(), node);
  }
  
  public IxCode.ClassDefinition[] getSourceClasses(){
    return (IxCode.ClassDefinition[])sourceClasses.toArray(new IxCode.ClassDefinition[0]);
  }
  
  public IxCode.ArraySymbol loadArray(IxCode.TypeRef component, int dimension){
    String arrayName = Strings.repeat("[", dimension) + component.getName();
    IxCode.ArraySymbol array = (IxCode.ArraySymbol) arrayMap.get(arrayName);
    if(array != null) return array;
    array = new IxCode.ArraySymbol(component, dimension, this);
    arrayMap.put(arrayName, array);
    return array;
  }
  
  public IxCode.ClassSymbol load(String className){
    IxCode.ClassSymbol clazz = lookup(className);
    if(clazz == null){
      JavaClass javaClass = table.load(className);
      if(javaClass != null){
        clazz = new ClassFileSymbol(javaClass, this);
        addClassFileSymbol(clazz);
      }else{
        try {
          clazz = new ClassObjectSymbol(
            Class.forName(className, true, Thread.currentThread().getContextClassLoader()), this
          );
          addClassFileSymbol(clazz);
        }catch(ClassNotFoundException e){          
        }
      }
    }
    return clazz;
  }
  
  public IxCode.ClassSymbol rootClass(){
    return load("java.lang.Object");
  }
  
  public IxCode.ClassSymbol lookup(String className){
    IxCode.ClassSymbol symbol = (IxCode.ClassSymbol) sourceClassMap.get(className);
    return (symbol != null) ? symbol : (IxCode.ClassSymbol) classFileMap.get(className);
  }
  
  private void addClassFileSymbol(IxCode.ClassSymbol symbol){
    classFileMap.put(symbol.getName(), symbol);
  }
}