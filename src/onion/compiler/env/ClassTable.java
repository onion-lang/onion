/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import java.util.*;

import org.apache.bcel.classfile.JavaClass;
import org.onion_lang.onion.compiler.env.java.ClassFileSymbol;
import org.onion_lang.onion.compiler.env.java.ClassFileTable;
import org.onion_lang.onion.compiler.env.java.ClassObjectSymbol;
import org.onion_lang.onion.compiler.util.Strings;
import org.onion_lang.onion.lang.core.IrClass;
import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class ClassTable {
  private List<IrClass> sourceClasses;
  private Map<String, IrClass> sourceClassMap;
  private Map<String, ClassSymbol> classFileMap;
  private Map<String, ArraySymbol> arrayMap;
  private ClassFileTable table;
  
  public ClassTable(String classPath) {
    sourceClasses  = new ArrayList<IrClass>();
    sourceClassMap = new HashMap<String, IrClass>();
    classFileMap   = new HashMap<String, ClassSymbol>();
    arrayMap       = new HashMap<String, ArraySymbol>();
    table          = new ClassFileTable(classPath);
  }
  
  public void addSourceClass(IrClass node){
    sourceClasses.add(node);
    sourceClassMap.put(node.getName(), node);
  }
  
  public IrClass[] getSourceClasses(){
    return (IrClass[])sourceClasses.toArray(new IrClass[0]);
  }
  
  public ArraySymbol loadArray(TypeRef component, int dimension){
    String arrayName = Strings.repeat("[", dimension) + component.getName();
    ArraySymbol array = (ArraySymbol) arrayMap.get(arrayName);
    if(array != null) return array;
    array = new ArraySymbol(component, dimension, this);
    arrayMap.put(arrayName, array);
    return array;
  }
  
  public ClassSymbol load(String className){
    ClassSymbol clazz = lookup(className);
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
  
  public ClassSymbol rootClass(){
    return load("java.lang.Object");
  }
  
  public ClassSymbol lookup(String className){
    ClassSymbol symbol = (ClassSymbol) sourceClassMap.get(className);
    return (symbol != null) ? symbol : (ClassSymbol) classFileMap.get(className);
  }
  
  private void addClassFileSymbol(ClassSymbol symbol){
    classFileMap.put(symbol.getName(), symbol);
  }
}