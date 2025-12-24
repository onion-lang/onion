/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.HashMap
import java.util.Map
import org.objectweb.asm.Type

/**
 * @author Kota Mizushima
 *
 */
object OnionTypeConversion {
  private final val asmTypeTable: Map[Int, TypedAST.BasicType] = new HashMap[Int, TypedAST.BasicType]
  private final val c2t: Map[Class[_], TypedAST.BasicType]     = new HashMap[Class[_], TypedAST.BasicType]

  asmTypeTable.put(Type.BYTE, TypedAST.BasicType.BYTE)
  asmTypeTable.put(Type.SHORT, TypedAST.BasicType.SHORT)
  asmTypeTable.put(Type.CHAR, TypedAST.BasicType.CHAR)
  asmTypeTable.put(Type.INT, TypedAST.BasicType.INT)
  asmTypeTable.put(Type.LONG, TypedAST.BasicType.LONG)
  asmTypeTable.put(Type.FLOAT, TypedAST.BasicType.FLOAT)
  asmTypeTable.put(Type.DOUBLE, TypedAST.BasicType.DOUBLE)
  asmTypeTable.put(Type.BOOLEAN, TypedAST.BasicType.BOOLEAN)
  asmTypeTable.put(Type.VOID, TypedAST.BasicType.VOID)

  c2t.put(classOf[Byte], TypedAST.BasicType.BYTE)
  c2t.put(classOf[Short], TypedAST.BasicType.SHORT)
  c2t.put(classOf[Char], TypedAST.BasicType.CHAR)
  c2t.put(classOf[Int], TypedAST.BasicType.INT)
  c2t.put(classOf[Long], TypedAST.BasicType.LONG)
  c2t.put(classOf[Float], TypedAST.BasicType.FLOAT)
  c2t.put(classOf[Double], TypedAST.BasicType.DOUBLE)
  c2t.put(classOf[Boolean], TypedAST.BasicType.BOOLEAN)
  c2t.put(classOf[Unit], TypedAST.BasicType.VOID)
}

class OnionTypeConversion(table: ClassTable) {
  import OnionTypeConversion._

  def toOnionType(klass: Class[_]): TypedAST.Type = {
    val returnType: TypedAST.Type = c2t.get(klass).asInstanceOf[TypedAST.Type]
    if (returnType != null) return returnType
    if (!klass.isArray) {
      val symbol: TypedAST.ClassType = table.load(klass.getName)
      if (symbol != null) {
        return symbol
      }
      else {
        return null
      }
    }
    if (klass.isArray) {
      var dimension: Int = 0
      var component: Class[_] = klass
      while(component.isArray) {
        dimension += 1
        component = component.getComponentType
      }
      val componentType: TypedAST.Type = toOnionType(component)
      return table.loadArray(componentType, dimension)
    }
    null
  }

  def toOnionType(asmType: Type): TypedAST.Type = {
    asmType.getSort match {
      case Type.VOID => TypedAST.BasicType.VOID
      case Type.BOOLEAN => TypedAST.BasicType.BOOLEAN
      case Type.BYTE => TypedAST.BasicType.BYTE
      case Type.SHORT => TypedAST.BasicType.SHORT
      case Type.CHAR => TypedAST.BasicType.CHAR
      case Type.INT => TypedAST.BasicType.INT
      case Type.LONG => TypedAST.BasicType.LONG
      case Type.FLOAT => TypedAST.BasicType.FLOAT
      case Type.DOUBLE => TypedAST.BasicType.DOUBLE
      case Type.OBJECT =>
        val className = asmType.getClassName
        table.load(className)
      case Type.ARRAY =>
        val elementType = asmType.getElementType
        val dimension = asmType.getDimensions
        val componentType = toOnionType(elementType)
        if (componentType != null) {
          table.loadArray(componentType, dimension)
        } else {
          null
        }
      case _ => null
    }
  }
}