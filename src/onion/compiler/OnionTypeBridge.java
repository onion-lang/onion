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

import onion.compiler.env.ClassTable;

import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class OnionTypeBridge {
  private static Map basicTypeTable = new HashMap(){{
    put(BasicType.BYTE, IxCode.BasicTypeRef.BYTE);
    put(BasicType.SHORT, IxCode.BasicTypeRef.SHORT);
    put(BasicType.CHAR, IxCode.BasicTypeRef.CHAR);
    put(BasicType.INT, IxCode.BasicTypeRef.INT);
    put(BasicType.LONG, IxCode.BasicTypeRef.LONG);
    put(BasicType.FLOAT, IxCode.BasicTypeRef.FLOAT);
    put(BasicType.DOUBLE, IxCode.BasicTypeRef.DOUBLE);
    put(BasicType.BOOLEAN, IxCode.BasicTypeRef.BOOLEAN);
    put(BasicType.VOID, IxCode.BasicTypeRef.VOID);
  }};
  
  private static Map c2t = new HashMap(){{
    put(byte.class, IxCode.BasicTypeRef.BYTE);
    put(short.class, IxCode.BasicTypeRef.SHORT);
    put(char.class, IxCode.BasicTypeRef.CHAR);
    put(int.class, IxCode.BasicTypeRef.INT);
    put(long.class, IxCode.BasicTypeRef.LONG);
    put(float.class, IxCode.BasicTypeRef.FLOAT);
    put(double.class, IxCode.BasicTypeRef.DOUBLE);
    put(boolean.class, IxCode.BasicTypeRef.BOOLEAN);
    put(void.class, IxCode.BasicTypeRef.VOID);
  }};
  
  private ClassTable table;
  
  public OnionTypeBridge(ClassTable table) {
    this.table = table;
  }
  
  public IxCode.TypeRef toOnionType(Class klass) {
    IxCode.TypeRef returnType = (IxCode.TypeRef) c2t.get(klass);
    if(returnType != null) return returnType;
    if(!klass.isArray()){
      IxCode.ClassTypeRef symbol = table.load(klass.getName());
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
      IxCode.TypeRef componentType = toOnionType(component);
      return table.loadArray(componentType, dimension);
    }
    return null;
  }
  
  public IxCode.TypeRef toOnionType(Type type) {
    IxCode.TypeRef returnType = (IxCode.TypeRef) basicTypeTable.get(type);
    if(returnType != null) return returnType;
    if(type instanceof ObjectType){
      ObjectType objType = (ObjectType)type;
      IxCode.ClassTypeRef symbol = table.load(objType.getClassName());
      if(symbol != null){
        return symbol;
      }else{
        return null;
      }
    }
    if(type instanceof ArrayType){
      ArrayType arrType = (ArrayType)type;
      IxCode.TypeRef component = toOnionType(arrType.getBasicType());
      if(component != null){        
        return table.loadArray(component, arrType.getDimensions());
      }
    }
    return null;
  }
}
