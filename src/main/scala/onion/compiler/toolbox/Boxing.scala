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

  def boxedType(table: ClassTable, `type`: TypedAST.BasicType): TypedAST.ClassType = {
    TABLE.find(row => row(0) eq `type`) match {
      case Some(row) => table.load(row(1).asInstanceOf[String])
      case None => throw new RuntimeException("")
    }
  }

  def boxing(table: ClassTable, node: TypedAST.Term): TypedAST.Term = {
    val `type`: TypedAST.Type = node.`type`
    if ((!`type`.isBasicType) || (`type` eq TypedAST.BasicType.VOID)) {
      throw new IllegalArgumentException("node type must be boxable type")
    }
    val aBoxedType: TypedAST.ClassType = boxedType(table, `type`.asInstanceOf[TypedAST.BasicType])

    // valueOf静的メソッドを探す
    val valueOfMethod = aBoxedType.findMethod("valueOf", Array[TypedAST.Term](node))
    if (valueOfMethod.length == 1) {
      return new TypedAST.CallStatic(aBoxedType, valueOfMethod(0), Array[TypedAST.Term](node))
    }

    throw new RuntimeException(s"couldn't find valueOf method for ${aBoxedType.name}")
  }

  /**
   * Returns the primitive type that the given reference type can be unboxed to.
   * Returns None if the type cannot be unboxed.
   */
  def unboxedType(table: ClassTable, `type`: TypedAST.Type): Option[TypedAST.BasicType] = {
    if (!`type`.isObjectType) None
    else TABLE.find { row =>
      val boxedTypeName = row(1).asInstanceOf[String]
      val boxedType = table.load(boxedTypeName)
      TypedAST.TypeRules.isAssignable(boxedType, `type`)
    }.map(row => row(0).asInstanceOf[TypedAST.BasicType])
  }

  def unboxing(table: ClassTable, node: TypedAST.Term, targetType: TypedAST.BasicType): TypedAST.Term = {
    val sourceType = node.`type`
    if (!sourceType.isObjectType) {
      throw new IllegalArgumentException("node type must be object type")
    }

    val aBoxedType = boxedType(table, targetType)
    if (!TypedAST.TypeRules.isAssignable(aBoxedType, sourceType)) {
      throw new IllegalArgumentException(s"cannot unbox ${sourceType} to ${targetType}")
    }

    // xxxValue()メソッドを呼び出す
    val methodName = targetType match {
      case TypedAST.BasicType.BOOLEAN => "booleanValue"
      case TypedAST.BasicType.BYTE => "byteValue"
      case TypedAST.BasicType.SHORT => "shortValue"
      case TypedAST.BasicType.CHAR => "charValue"
      case TypedAST.BasicType.INT => "intValue"
      case TypedAST.BasicType.LONG => "longValue"
      case TypedAST.BasicType.FLOAT => "floatValue"
      case TypedAST.BasicType.DOUBLE => "doubleValue"
      case _ => throw new IllegalArgumentException(s"cannot unbox to ${targetType}")
    }

    val unboxMethod = aBoxedType.findMethod(methodName, Array[TypedAST.Term]())
    if (unboxMethod.length == 1) {
      return new TypedAST.Call(node, unboxMethod(0), Array[TypedAST.Term]())
    }

    throw new RuntimeException(s"couldn't find ${methodName} method")
  }
}
