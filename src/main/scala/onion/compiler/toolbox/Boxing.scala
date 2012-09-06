/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import onion.compiler.IRT
import onion.compiler.ClassTable

/**
 * @author Kota Mizushima
 *         Date: 2005/07/10
 */
object Boxing {
  private final val TABLE: Array[Array[AnyRef]] = Array(Array(IRT.BasicTypeRef.BOOLEAN, "java.lang.Boolean"), Array(IRT.BasicTypeRef.BYTE, "java.lang.Byte"), Array(IRT.BasicTypeRef.SHORT, "java.lang.Short"), Array(IRT.BasicTypeRef.CHAR, "java.lang.Character"), Array(IRT.BasicTypeRef.INT, "java.lang.Integer"), Array(IRT.BasicTypeRef.LONG, "java.lang.Long"), Array(IRT.BasicTypeRef.FLOAT, "java.lang.Float"), Array(IRT.BasicTypeRef.DOUBLE, "java.lang.Double"))

  private def boxedType(table: ClassTable, `type`: IRT.BasicTypeRef): IRT.ClassTypeRef = {
    for (row <- TABLE) {
      if (row(0) eq `type`) return table.load(row(1).asInstanceOf[String])
    }
    throw new RuntimeException("")
  }

  def boxing(table: ClassTable, node: IRT.Term): IRT.Term = {
    val `type`: IRT.TypeRef = node.`type`
    if ((!`type`.isBasicType) || (`type` eq IRT.BasicTypeRef.VOID)) {
      throw new IllegalArgumentException("node type must be boxable type")
    }
    val aBoxedType: IRT.ClassTypeRef = boxedType(table, `type`.asInstanceOf[IRT.BasicTypeRef])
    val cs: Array[IRT.ConstructorRef] = aBoxedType.constructors
    var i: Int = 0

    while (i < cs.length) {
      val args: Array[IRT.TypeRef] = cs(i).getArgs
      if ((args.length == 1) && (args(i) eq `type`)) {
        return new IRT.NewObject(cs(i), Array[IRT.Term](node))
      }
      i += 1
    }
    throw new RuntimeException("couldn't find matched constructor")
  }
}
