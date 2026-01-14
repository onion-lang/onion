/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.util.{WeakHashMap => JWeakHashMap}
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

  private val basicToIndex: Map[TypedAST.BasicType, Int] =
    TABLE.zipWithIndex.map { (row, index) =>
      row(0).asInstanceOf[TypedAST.BasicType] -> index
    }.toMap

  private val boxedNameToBasic: Map[String, TypedAST.BasicType] =
    TABLE.map { row =>
      row(1).asInstanceOf[String] -> row(0).asInstanceOf[TypedAST.BasicType]
    }.toMap

  private val boxedTypeCache = new JWeakHashMap[ClassTable, Array[TypedAST.ClassType]]()
  private val valueOfMethodCache = new JWeakHashMap[ClassTable, Array[TypedAST.Method]]()
  private val unboxMethodCache = new JWeakHashMap[ClassTable, Array[TypedAST.Method]]()
  private val emptyTerms = Array.empty[TypedAST.Term]

  private def boxedTypeIndex(`type`: TypedAST.BasicType): Int =
    basicToIndex.getOrElse(`type`, throw new RuntimeException("unknown boxed type"))

  private def cachedBoxedType(table: ClassTable, `type`: TypedAST.BasicType): TypedAST.ClassType = {
    val index = boxedTypeIndex(`type`)
    var cache = boxedTypeCache.get(table)
    if (cache == null) {
      cache = new Array[TypedAST.ClassType](TABLE.length)
      boxedTypeCache.put(table, cache)
    }
    var boxed = cache(index)
    if (boxed == null) {
      val boxedName = TABLE(index)(1).asInstanceOf[String]
      boxed = table.load(boxedName)
      cache(index) = boxed
    }
    boxed
  }

  private def cachedMethod(
    cache: JWeakHashMap[ClassTable, Array[TypedAST.Method]],
    table: ClassTable
  ): Array[TypedAST.Method] = {
    var methods = cache.get(table)
    if (methods == null) {
      methods = new Array[TypedAST.Method](TABLE.length)
      cache.put(table, methods)
    }
    methods
  }

  private def cachedValueOfMethod(table: ClassTable, node: TypedAST.Term, `type`: TypedAST.BasicType): TypedAST.Method = {
    val index = boxedTypeIndex(`type`)
    val methods = cachedMethod(valueOfMethodCache, table)
    var method = methods(index)
    if (method == null) {
      val boxed = cachedBoxedType(table, `type`)
      val args = Array[TypedAST.Term](node)
      val candidates = boxed.findMethod("valueOf", args)
      if (candidates.length == 1) {
        method = candidates(0)
        methods(index) = method
      } else {
        throw new RuntimeException(s"couldn't find valueOf method for ${boxed.name}")
      }
    }
    method
  }

  private def cachedUnboxMethod(table: ClassTable, `type`: TypedAST.BasicType, methodName: String): TypedAST.Method = {
    val index = boxedTypeIndex(`type`)
    val methods = cachedMethod(unboxMethodCache, table)
    var method = methods(index)
    if (method == null) {
      val boxed = cachedBoxedType(table, `type`)
      val candidates = boxed.findMethod(methodName, emptyTerms)
      if (candidates.length == 1) {
        method = candidates(0)
        methods(index) = method
      } else {
        throw new RuntimeException(s"couldn't find ${methodName} method")
      }
    }
    method
  }

  def boxedType(table: ClassTable, `type`: TypedAST.BasicType): TypedAST.ClassType = {
    cachedBoxedType(table, `type`)
  }

  def boxing(table: ClassTable, node: TypedAST.Term): TypedAST.Term = {
    val `type`: TypedAST.Type = node.`type`
    if ((!`type`.isBasicType) || (`type` eq TypedAST.BasicType.VOID)) {
      throw new IllegalArgumentException("node type must be boxable type")
    }
    val basicType = `type`.asInstanceOf[TypedAST.BasicType]
    val aBoxedType: TypedAST.ClassType = boxedType(table, basicType)
    // valueOf静的メソッドを探す
    val valueOfMethod = cachedValueOfMethod(table, node, basicType)
    val args = Array[TypedAST.Term](node)
    new TypedAST.CallStatic(aBoxedType, valueOfMethod, args)
  }

  /**
   * Returns the primitive type that the given reference type can be unboxed to.
   * Returns None if the type cannot be unboxed.
   */
  def unboxedType(table: ClassTable, `type`: TypedAST.Type): Option[TypedAST.BasicType] = {
    if (!`type`.isObjectType) None
    else {
      `type` match {
        case ct: TypedAST.ClassType =>
          boxedNameToBasic.get(ct.name) match {
            case some @ Some(_) => return some
            case None =>
          }
        case _ =>
      }
      TABLE.find { row =>
        val boxedType = cachedBoxedType(table, row(0).asInstanceOf[TypedAST.BasicType])
        TypedAST.TypeRules.isAssignable(boxedType, `type`)
      }.map(row => row(0).asInstanceOf[TypedAST.BasicType])
    }
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

    val cached = cachedUnboxMethod(table, targetType, methodName)
    new TypedAST.Call(node, cached, emptyTerms)
  }

  /** Try to unbox a term to a numeric primitive, returning the original term if not possible */
  def tryUnboxToNumeric(table: ClassTable, term: TypedAST.Term, isNumeric: TypedAST.BasicType => Boolean): TypedAST.Term =
    if (term.isBasicType) term
    else unboxedType(table, term.`type`).filter(isNumeric) match {
      case Some(bt) => unboxing(table, term, bt)
      case None => term
    }

  /** Try to unbox a term to boolean, returning the original term if not possible */
  def tryUnboxToBoolean(table: ClassTable, term: TypedAST.Term): TypedAST.Term =
    if (term.isBasicType) term
    else unboxedType(table, term.`type`) match {
      case Some(TypedAST.BasicType.BOOLEAN) => unboxing(table, term, TypedAST.BasicType.BOOLEAN)
      case _ => term
    }

  /** Try to unbox a term to an integer primitive, returning the original term if not possible */
  def tryUnboxToInteger(table: ClassTable, term: TypedAST.Term): TypedAST.Term =
    if (term.isBasicType) term
    else unboxedType(table, term.`type`).filter(_.isInteger) match {
      case Some(bt) => unboxing(table, term, bt)
      case None => term
    }
}
