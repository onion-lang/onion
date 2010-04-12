/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.lang.core;

import org.onion_lang.onion.lang.core.type.BasicTypeRef;
import org.onion_lang.onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/04/17
 */
public abstract class IrExpression implements IrNode {
  public IrExpression() {}
  
  public abstract TypeRef type();
  
  public boolean isBasicType(){ 
    return type().isBasicType(); 
  }
  
  public boolean isArrayType() { 
    return type().isArrayType();
  }
  
  public boolean isClassType() {
    return type().isClassType();
  }
  
  public boolean isNullType() { return type().isNullType(); }
  
  public boolean isReferenceType(){ return type().isObjectType(); }
  
  public static IrExpression defaultValue(TypeRef type){
    if(type == BasicTypeRef.CHAR) return new IrChar((char)0); 
    if(type == BasicTypeRef.BYTE)return new IrByte((byte)0);
    if(type == BasicTypeRef.SHORT) return new IrShort((short)0); 
    if(type == BasicTypeRef.INT) return new IrInt(0); 
    if(type == BasicTypeRef.LONG) return new IrLong(0); 
    if(type == BasicTypeRef.FLOAT) return new IrFloat(0.0f); 
    if(type == BasicTypeRef.DOUBLE) return new IrDouble(0.0);
    if(type == BasicTypeRef.BOOLEAN) return new IrBool(false);
    if(type.isObjectType()) return new IrNull();
    return null;
  }
}