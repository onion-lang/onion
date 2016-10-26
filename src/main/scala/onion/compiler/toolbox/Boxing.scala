/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
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
  private final val TABLE: Array[Array[AnyRef]] = Array(Array(IRT.BasicType.BOOLEAN, "java.lang.Boolean"), Array(IRT.BasicType.BYTE, "java.lang.Byte"), Array(IRT.BasicType.SHORT, "java.lang.Short"), Array(IRT.BasicType.CHAR, "java.lang.Character"), Array(IRT.BasicType.INT, "java.lang.Integer"), Array(IRT.BasicType.LONG, "java.lang.Long"), Array(IRT.BasicType.FLOAT, "java.lang.Float"), Array(IRT.BasicType.DOUBLE, "java.lang.Double"))

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
    var i: Int = 0

    while (i < cs.length) {
      val args: Array[IRT.Type] = cs(i).getArgs
      if ((args.length == 1) && (args(i) eq `type`)) {
        return new IRT.NewObject(cs(i), Array[IRT.Term](node))
      }
      i += 1
    }
    throw new RuntimeException("couldn't find matched constructor")
  }
}
