/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler.util;

import org.onion_lang.onion.compiler.env.ClassTable;
import org.onion_lang.onion.lang.core.IrExpression;
import org.onion_lang.onion.lang.core.IrNew;
import org.onion_lang.onion.lang.core.type.*;

/**
 * @author Kota Mizushima
 * Date: 2005/07/10
 */
public class Boxing {
  private static final Object[][] TABLE = {
    {BasicTypeRef.BOOLEAN, "java.lang.Boolean"   },
    {BasicTypeRef.BYTE,    "java.lang.Byte"      },
    {BasicTypeRef.SHORT,   "java.lang.Short"     },
    {BasicTypeRef.CHAR,    "java.lang.Character" },
    {BasicTypeRef.INT,     "java.lang.Integer"   },
    {BasicTypeRef.LONG,    "java.lang.Long"      },
    {BasicTypeRef.FLOAT,   "java.lang.Float"     },
    {BasicTypeRef.DOUBLE,  "java.lang.Double"    },
  };
  
  private Boxing() {
  }
  
  private static ClassSymbol boxedType(ClassTable table, BasicTypeRef type){
    for(int i  = 0; i < TABLE.length; i++){
      if(TABLE[i][0] == type){
        return table.load(((String)TABLE[i][1]));
      }
    }
    throw new RuntimeException("");
  }
  
  public static IrExpression boxing(ClassTable table, IrExpression node){
    TypeRef type = node.type();
    if((!type.isBasicType()) || type == BasicTypeRef.VOID){
      throw new IllegalArgumentException("node type must be boxable type");
    }
    ClassSymbol boxedType = boxedType(table, (BasicTypeRef)type);
    ConstructorSymbol[] cs = boxedType.getConstructors();
    for(int i = 0; i < cs.length; i++){
      TypeRef[] args = cs[i].getArgs();
      if(args.length == 1 && args[i] == type){
        return new IrNew(cs[i], new IrExpression[]{node});
      }
    }
    throw new RuntimeException("couldn't find matched constructor");
  }
}
