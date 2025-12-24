/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import onion.compiler.TypedAST
import onion.compiler.ClassTable

/**
 * @author Kota Mizushima
 *
 */
object Boxing {
  private final val TABLE: Array[Array[AnyRef]] = Array(
    Array[AnyRef](TypedAST.BasicType.BOOLEAN, "java.lang.Boolean"),
    Array[AnyRef](TypedAST.BasicType.BYTE, "java.lang.Byte"),
    Array[AnyRef](TypedAST.BasicType.SHORT, "java.lang.Short"),
    Array[AnyRef](TypedAST.BasicType.CHAR, "java.lang.Character"),
    Array[AnyRef](TypedAST.BasicType.INT, "java.lang.Integer"),
    Array[AnyRef](TypedAST.BasicType.LONG, "java.lang.Long"),
    Array[AnyRef](TypedAST.BasicType.FLOAT, "java.lang.Float"),
    Array[AnyRef](TypedAST.BasicType.DOUBLE, "java.lang.Double")
  )

  private def boxedType(table: ClassTable, `type`: TypedAST.BasicType): TypedAST.ClassType = {
    for (row <- TABLE) {
      if (row(0) eq `type`) return table.load(row(1).asInstanceOf[String])
    }
    throw new RuntimeException("")
  }

  def boxing(table: ClassTable, node: TypedAST.Term): TypedAST.Term = {
    val `type`: TypedAST.Type = node.`type`
    if ((!`type`.isBasicType) || (`type` eq TypedAST.BasicType.VOID)) {
      throw new IllegalArgumentException("node type must be boxable type")
    }
    val aBoxedType: TypedAST.ClassType = boxedType(table, `type`.asInstanceOf[TypedAST.BasicType])
    val cs: Array[TypedAST.ConstructorRef] = aBoxedType.constructors
    for(i <- cs.indices) {
      val args: Array[TypedAST.Type] = cs(i).getArgs
      if ((args.length == 1) && (args(i) eq `type`)) {
        return new TypedAST.NewObject(cs(i), Array[TypedAST.Term](node))
      }
    }
    throw new RuntimeException("couldn't find matched constructor")
  }
}
