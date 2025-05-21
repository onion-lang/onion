/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import onion.compiler.IRT
import onion.compiler.ClassTable

/**
 * @author Kota Mizushima
 *
 */
object Boxing {
  private final val TABLE: Array[Array[AnyRef]] = Array(
    Array[AnyRef](IRT.BasicType.BOOLEAN, "java.lang.Boolean"),
    Array[AnyRef](IRT.BasicType.BYTE, "java.lang.Byte"),
    Array[AnyRef](IRT.BasicType.SHORT, "java.lang.Short"),
    Array[AnyRef](IRT.BasicType.CHAR, "java.lang.Character"),
    Array[AnyRef](IRT.BasicType.INT, "java.lang.Integer"),
    Array[AnyRef](IRT.BasicType.LONG, "java.lang.Long"),
    Array[AnyRef](IRT.BasicType.FLOAT, "java.lang.Float"),
    Array[AnyRef](IRT.BasicType.DOUBLE, "java.lang.Double")
  )

  private def boxedType(table: ClassTable, `type`: IRT.BasicType): IRT.ClassType = {
    for (row <- TABLE) {
      if (row(0) eq `type`) return table.load(row(1).asInstanceOf[String])
    }
    throw new RuntimeException("")
  }

  def boxing(table: ClassTable, node: IRT.Term): IRT.Term = {
    val `type`: IRT.Type = node.`type`
    if ((!`type`.isBasicType) || (`type` eq IRT.BasicType.VOID)) {
      throw new IllegalArgumentException("node type must be boxable type")
    }
    val aBoxedType: IRT.ClassType = boxedType(table, `type`.asInstanceOf[IRT.BasicType])
    val cs: Array[IRT.ConstructorRef] = aBoxedType.constructors
    for(i <- cs.indices) {
      val args: Array[IRT.Type] = cs(i).getArgs
      if ((args.length == 1) && (args(i) eq `type`)) {
        return new IRT.NewObject(cs(i), Array[IRT.Term](node))
      }
    }
    throw new RuntimeException("couldn't find matched constructor")
  }
}
