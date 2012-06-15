/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox;

import onion.compiler.IRT;
import onion.compiler.ClassTable;


/**
 * @author Kota Mizushima
 * Date: 2005/07/10
 */
public class Boxing {
  private static final Object[][] TABLE = {
    {IRT.BasicTypeRef.BOOLEAN, "java.lang.Boolean"   },
    {IRT.BasicTypeRef.BYTE,    "java.lang.Byte"      },
    {IRT.BasicTypeRef.SHORT,   "java.lang.Short"     },
    {IRT.BasicTypeRef.CHAR,    "java.lang.Character" },
    {IRT.BasicTypeRef.INT,     "java.lang.Integer"   },
    {IRT.BasicTypeRef.LONG,    "java.lang.Long"      },
    {IRT.BasicTypeRef.FLOAT,   "java.lang.Float"     },
    {IRT.BasicTypeRef.DOUBLE,  "java.lang.Double"    },
  };
  
  private Boxing() {
  }
  
  private static IRT.ClassTypeRef boxedType(ClassTable table, IRT.BasicTypeRef type){
    for(int i  = 0; i < TABLE.length; i++){
      if(TABLE[i][0] == type){
        return table.load(((String)TABLE[i][1]));
      }
    }
    throw new RuntimeException("");
  }
  
  public static IRT.Term boxing(ClassTable table, IRT.Term node){
    IRT.TypeRef type = node.type();
    if((!type.isBasicType()) || type == IRT.BasicTypeRef.VOID){
      throw new IllegalArgumentException("node type must be boxable type");
    }
    IRT.ClassTypeRef boxedType = boxedType(table, (IRT.BasicTypeRef)type);
    IRT.ConstructorRef[] cs = boxedType.constructors();
    for(int i = 0; i < cs.length; i++){
      IRT.TypeRef[] args = cs[i].getArgs();
      if(args.length == 1 && args[i] == type){
        return new IRT.NewObject(cs[i], new IRT.Term[]{node});
      }
    }
    throw new RuntimeException("couldn't find matched constructor");
  }
}
