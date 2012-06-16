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
  private static final Map<BasicType, IRT.BasicTypeRef> basicTypeTable = new HashMap<BasicType, IRT.BasicTypeRef>();
  private static final Map<Class<?>, IRT.BasicTypeRef> c2t = new HashMap<Class<?>, IRT.BasicTypeRef>();

  static {
    basicTypeTable.put(BasicType.BYTE, IRT.BasicTypeRef.BYTE);
    basicTypeTable.put(BasicType.SHORT, IRT.BasicTypeRef.SHORT);
    basicTypeTable.put(BasicType.CHAR, IRT.BasicTypeRef.CHAR);
    basicTypeTable.put(BasicType.INT, IRT.BasicTypeRef.INT);
    basicTypeTable.put(BasicType.LONG, IRT.BasicTypeRef.LONG);
    basicTypeTable.put(BasicType.FLOAT, IRT.BasicTypeRef.FLOAT);
    basicTypeTable.put(BasicType.DOUBLE, IRT.BasicTypeRef.DOUBLE);
    basicTypeTable.put(BasicType.BOOLEAN, IRT.BasicTypeRef.BOOLEAN);
    basicTypeTable.put(BasicType.VOID, IRT.BasicTypeRef.VOID);


    c2t.put(byte.class, IRT.BasicTypeRef.BYTE);
    c2t.put(short.class, IRT.BasicTypeRef.SHORT);
    c2t.put(char.class, IRT.BasicTypeRef.CHAR);
    c2t.put(int.class, IRT.BasicTypeRef.INT);
    c2t.put(long.class, IRT.BasicTypeRef.LONG);
    c2t.put(float.class, IRT.BasicTypeRef.FLOAT);
    c2t.put(double.class, IRT.BasicTypeRef.DOUBLE);
    c2t.put(boolean.class, IRT.BasicTypeRef.BOOLEAN);
    c2t.put(void.class, IRT.BasicTypeRef.VOID);
  }
  
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
      do {
        dimension++;
        component = component.getComponentType();
      } while (component.getComponentType() != null);
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
