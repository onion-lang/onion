/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.util;

import onion.compiler.IxCode;
import onion.compiler.env.ClassTable;


/**
 * @author Kota Mizushima
 * Date: 2005/07/10
 */
public class Boxing {
  private static final Object[][] TABLE = {
    {IxCode.BasicTypeRef.BOOLEAN, "java.lang.Boolean"   },
    {IxCode.BasicTypeRef.BYTE,    "java.lang.Byte"      },
    {IxCode.BasicTypeRef.SHORT,   "java.lang.Short"     },
    {IxCode.BasicTypeRef.CHAR,    "java.lang.Character" },
    {IxCode.BasicTypeRef.INT,     "java.lang.Integer"   },
    {IxCode.BasicTypeRef.LONG,    "java.lang.Long"      },
    {IxCode.BasicTypeRef.FLOAT,   "java.lang.Float"     },
    {IxCode.BasicTypeRef.DOUBLE,  "java.lang.Double"    },
  };
  
  private Boxing() {
  }
  
  private static IxCode.ClassSymbol boxedType(ClassTable table, IxCode.BasicTypeRef type){
    for(int i  = 0; i < TABLE.length; i++){
      if(TABLE[i][0] == type){
        return table.load(((String)TABLE[i][1]));
      }
    }
    throw new RuntimeException("");
  }
  
  public static IxCode.IrExpression boxing(ClassTable table, IxCode.IrExpression node){
    IxCode.TypeRef type = node.type();
    if((!type.isBasicType()) || type == IxCode.BasicTypeRef.VOID){
      throw new IllegalArgumentException("node type must be boxable type");
    }
    IxCode.ClassSymbol boxedType = boxedType(table, (IxCode.BasicTypeRef)type);
    IxCode.ConstructorSymbol[] cs = boxedType.getConstructors();
    for(int i = 0; i < cs.length; i++){
      IxCode.TypeRef[] args = cs[i].getArgs();
      if(args.length == 1 && args[i] == type){
        return new IxCode.IrNew(cs[i], new IxCode.IrExpression[]{node});
      }
    }
    throw new RuntimeException("couldn't find matched constructor");
  }
}
