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

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class OnionTypeBridge {
  private static Map basicTypeTable = new HashMap(){{
    put(BasicType.BYTE, IRT.BasicTypeRef.BYTE);
    put(BasicType.SHORT, IRT.BasicTypeRef.SHORT);
    put(BasicType.CHAR, IRT.BasicTypeRef.CHAR);
    put(BasicType.INT, IRT.BasicTypeRef.INT);
    put(BasicType.LONG, IRT.BasicTypeRef.LONG);
    put(BasicType.FLOAT, IRT.BasicTypeRef.FLOAT);
    put(BasicType.DOUBLE, IRT.BasicTypeRef.DOUBLE);
    put(BasicType.BOOLEAN, IRT.BasicTypeRef.BOOLEAN);
    put(BasicType.VOID, IRT.BasicTypeRef.VOID);
  }};
  
  private static Map c2t = new HashMap(){{
    put(byte.class, IRT.BasicTypeRef.BYTE);
    put(short.class, IRT.BasicTypeRef.SHORT);
    put(char.class, IRT.BasicTypeRef.CHAR);
    put(int.class, IRT.BasicTypeRef.INT);
    put(long.class, IRT.BasicTypeRef.LONG);
    put(float.class, IRT.BasicTypeRef.FLOAT);
    put(double.class, IRT.BasicTypeRef.DOUBLE);
    put(boolean.class, IRT.BasicTypeRef.BOOLEAN);
    put(void.class, IRT.BasicTypeRef.VOID);
  }};
  
  private ClassTable table;
  
  public OnionTypeBridge(ClassTable table) {
    this.table = table;
  }
  
  public IRT.TypeRef toOnionType(Class klass) {
    IRT.TypeRef returnType = (IRT.TypeRef) c2t.get(klass);
    if(returnType != null) return returnType;
    if(!klass.isArray()){
      IRT.ClassTypeRef symbol = table.load(klass.getName());
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
      IRT.TypeRef componentType = toOnionType(component);
      return table.loadArray(componentType, dimension);
    }
    return null;
  }
  
  public IRT.TypeRef toOnionType(Type type) {
    IRT.TypeRef returnType = (IRT.TypeRef) basicTypeTable.get(type);
    if(returnType != null) return returnType;
    if(type instanceof ObjectType){
      ObjectType objType = (ObjectType)type;
      IRT.ClassTypeRef symbol = table.load(objType.getClassName());
      if(symbol != null){
        return symbol;
      }else{
        return null;
      }
    }
    if(type instanceof ArrayType){
      ArrayType arrType = (ArrayType)type;
      IRT.TypeRef component = toOnionType(arrType.getBasicType());
      if(component != null){        
        return table.loadArray(component, arrType.getDimensions());
      }
    }
    return null;
  }
}
