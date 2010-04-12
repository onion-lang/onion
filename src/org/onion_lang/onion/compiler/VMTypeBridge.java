/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler;

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.generic.*;
import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class VMTypeBridge {
  private static Map basicTypeTable = new HashMap(){{
    put(BasicTypeRef.BYTE,			BasicType.BYTE);
    put(BasicTypeRef.SHORT,		BasicType.SHORT);
    put(BasicTypeRef.CHAR,			BasicType.CHAR);
    put(BasicTypeRef.INT,			BasicType.INT);
    put(BasicTypeRef.LONG, 		BasicType.LONG);
    put(BasicTypeRef.FLOAT,		BasicType.FLOAT);
    put(BasicTypeRef.DOUBLE, 	BasicType.DOUBLE);
    put(BasicTypeRef.BOOLEAN,	BasicType.BOOLEAN);
    put(BasicTypeRef.VOID,			BasicType.VOID);
  }};
  
  public VMTypeBridge() {
  }

  public Type toVMType(TypeRef type){
    if(type.isBasicType()){
      return (BasicType) basicTypeTable.get(type);
    }else if(type.isArrayType()){
      ArraySymbol arrayType = (ArraySymbol)type;
      return new ArrayType(
        toVMType(arrayType.getComponent()), arrayType.getDimension());
    }else if(type.isClassType()){
      return new ObjectType(((ClassSymbol)type).getName());
    }else{
      return Type.NULL;
    }
  }
}
