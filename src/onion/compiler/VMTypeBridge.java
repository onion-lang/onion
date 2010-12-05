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

import org.apache.bcel.generic.*;

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class VMTypeBridge {
  private static Map basicTypeTable = new HashMap(){{
    put(IxCode.BasicTypeRef.BYTE,			BasicType.BYTE);
    put(IxCode.BasicTypeRef.SHORT,		BasicType.SHORT);
    put(IxCode.BasicTypeRef.CHAR,			BasicType.CHAR);
    put(IxCode.BasicTypeRef.INT,			BasicType.INT);
    put(IxCode.BasicTypeRef.LONG, 		BasicType.LONG);
    put(IxCode.BasicTypeRef.FLOAT,		BasicType.FLOAT);
    put(IxCode.BasicTypeRef.DOUBLE, 	BasicType.DOUBLE);
    put(IxCode.BasicTypeRef.BOOLEAN,	BasicType.BOOLEAN);
    put(IxCode.BasicTypeRef.VOID,			BasicType.VOID);
  }};
  
  public VMTypeBridge() {
  }

  public Type toVMType(IxCode.TypeRef type){
    if(type.isBasicType()){
      return (BasicType) basicTypeTable.get(type);
    }else if(type.isArrayType()){
      IxCode.ArrayTypeRef arrayType = (IxCode.ArrayTypeRef)type;
      return new ArrayType(
        toVMType(arrayType.getComponent()), arrayType.getDimension());
    }else if(type.isClassType()){
      return new ObjectType(((IxCode.ClassTypeRef)type).getName());
    }else{
      return Type.NULL;
    }
  }
}
