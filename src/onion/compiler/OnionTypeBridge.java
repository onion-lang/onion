/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;
import org.onion_lang.onion.compiler.env.ClassTable;
import org.onion_lang.onion.lang.core.type.BasicTypeRef;
import org.onion_lang.onion.lang.core.type.ClassSymbol;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class OnionTypeBridge {
  private static Map basicTypeTable = new HashMap(){{
    put(BasicType.BYTE, BasicTypeRef.BYTE);
    put(BasicType.SHORT, BasicTypeRef.SHORT);
    put(BasicType.CHAR, BasicTypeRef.CHAR);
    put(BasicType.INT, BasicTypeRef.INT);
    put(BasicType.LONG, BasicTypeRef.LONG);
    put(BasicType.FLOAT, BasicTypeRef.FLOAT);
    put(BasicType.DOUBLE, BasicTypeRef.DOUBLE);
    put(BasicType.BOOLEAN, BasicTypeRef.BOOLEAN);
    put(BasicType.VOID, BasicTypeRef.VOID);
  }};
  
  private static Map c2t = new HashMap(){{
    put(byte.class, BasicTypeRef.BYTE);
    put(short.class, BasicTypeRef.SHORT);
    put(char.class, BasicTypeRef.CHAR);
    put(int.class, BasicTypeRef.INT);
    put(long.class, BasicTypeRef.LONG);
    put(float.class, BasicTypeRef.FLOAT);
    put(double.class, BasicTypeRef.DOUBLE);
    put(boolean.class, BasicTypeRef.BOOLEAN);
    put(void.class, BasicTypeRef.VOID);
  }};
  
  private ClassTable table;
  
  public OnionTypeBridge(ClassTable table) {
    this.table = table;
  }
  
  public TypeRef toOnionType(Class klass) {
    TypeRef returnType = (TypeRef) c2t.get(klass);
    if(returnType != null) return returnType;
    if(!klass.isArray()){
      ClassSymbol symbol = table.load(klass.getName());
      if(symbol != null){
        return symbol;
      }else{
        return null;
      }
    }
    if(klass.isArray()){
      int dimension = 0;
      Class component = null;
      while(true){
        dimension++;
        component = klass.getComponentType();
        if(component.getComponentType() == null) break;
      }
      TypeRef componentType = toOnionType(component);
      return table.loadArray(componentType, dimension);
    }
    return null;
  }
  
  public TypeRef toOnionType(Type type) {
    TypeRef returnType = (TypeRef) basicTypeTable.get(type);
    if(returnType != null) return returnType;
    if(type instanceof ObjectType){
      ObjectType objType = (ObjectType)type;
      ClassSymbol symbol = table.load(objType.getClassName());
      if(symbol != null){
        return symbol;
      }else{
        return null;
      }
    }
    if(type instanceof ArrayType){
      ArrayType arrType = (ArrayType)type;
      TypeRef component = toOnionType(arrType.getBasicType());
      if(component != null){        
        return table.loadArray(component, arrType.getDimensions());
      }
    }
    return null;
  }
}
