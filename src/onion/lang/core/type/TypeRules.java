/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;


/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
public class TypeRules {
  
  private TypeRules() {
  }

  public static boolean isSuperType(
    TypeRef left, TypeRef right){
    if(left.isBasicType()){
      if(right.isBasicType()){
        return isSuperTypeForBasic((BasicTypeRef) left, (BasicTypeRef) right);
      }
      return false;
    }
    
    if(left.isClassType()){
      if(right.isClassType()){
        return isSuperTypeForClass((ClassSymbol) left, (ClassSymbol) right);
      }
      if(right.isArrayType()){
        return left == ((ArraySymbol) right).getSuperClass();
      }
      if(right.isNullType()){
        return true;
      }
      return false;
    }
    if(left.isArrayType()){
      if(right.isArrayType()){
        return isSuperTypeForArray((ArraySymbol) left, (ArraySymbol) right);
      }
      if(right.isNullType()){
        return true;
      }
      return false;
    }
    return false;
  }
  
  public static boolean isAssignable(TypeRef left, TypeRef right){
    return isSuperType(left, right);
  }
  
  private static boolean isSuperTypeForArray(
    ArraySymbol left, ArraySymbol right){
    return isSuperType(left.getBase(), right.getBase());
  }
  
  private static boolean isSuperTypeForClass(ClassSymbol left, ClassSymbol right){
    if(right == null) return false;
    if(left == right) return true;
    if(isSuperTypeForClass(left, right.getSuperClass())) return true;
    for(int i = 0; i < right.getInterfaces().length; i++){
      if(isSuperTypeForClass(left, right.getInterfaces()[i])) return true;
    }
    return false;
  }
  
  private static boolean isSuperTypeForBasic(BasicTypeRef left, BasicTypeRef right){
    if(left == BasicTypeRef.DOUBLE){
      if(
        right == BasicTypeRef.CHAR		||
        right == BasicTypeRef.BYTE		|| right == BasicTypeRef.SHORT || 
        right == BasicTypeRef.INT		|| right == BasicTypeRef.LONG	|| 
        right == BasicTypeRef.FLOAT	|| right == BasicTypeRef.DOUBLE){
        return true;
      }else{
        return false;
      }
    }
    if(left == BasicTypeRef.FLOAT){
      if(
        right == BasicTypeRef.CHAR		|| right == BasicTypeRef.BYTE	|| 
        right == BasicTypeRef.SHORT	|| right == BasicTypeRef.INT		|| 
        right == BasicTypeRef.LONG		|| right == BasicTypeRef.FLOAT){
        return true;
      }else{
        return false;
      }
    }
    if(left == BasicTypeRef.LONG){
      if(
        right == BasicTypeRef.CHAR		|| right == BasicTypeRef.BYTE	||
        right == BasicTypeRef.SHORT	|| right == BasicTypeRef.INT		|| 
        right == BasicTypeRef.LONG){
        return true;
      }else{
        return false;
      }
    }
    if(left == BasicTypeRef.INT){
      if(
        right == BasicTypeRef.CHAR		|| right == BasicTypeRef.BYTE	|| 
        right == BasicTypeRef.SHORT	|| right == BasicTypeRef.INT){
        return true;
      }else{
        return false;
      }
    }
    if(left == BasicTypeRef.SHORT){
      if(right == BasicTypeRef.BYTE || right == BasicTypeRef.SHORT){
        return true;
      }else{
        return false;
      }
    }
    if(left == BasicTypeRef.BOOLEAN && right == BasicTypeRef.BOOLEAN) return true;
    if(left == BasicTypeRef.BYTE && right == BasicTypeRef.BYTE) return true;
    if(left == BasicTypeRef.CHAR && right == BasicTypeRef.CHAR) return true;
    return false;
  }
}