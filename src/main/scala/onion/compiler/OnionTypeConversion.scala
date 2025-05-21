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
  private final val asmTypeTable: Map[Int, IRT.BasicType] = new HashMap[Int, IRT.BasicType]
  private final val c2t: Map[Class[_], IRT.BasicType]     = new HashMap[Class[_], IRT.BasicType]

  asmTypeTable.put(Type.BYTE, IRT.BasicType.BYTE)
  asmTypeTable.put(Type.SHORT, IRT.BasicType.SHORT)
  asmTypeTable.put(Type.CHAR, IRT.BasicType.CHAR)
  asmTypeTable.put(Type.INT, IRT.BasicType.INT)
  asmTypeTable.put(Type.LONG, IRT.BasicType.LONG)
  asmTypeTable.put(Type.FLOAT, IRT.BasicType.FLOAT)
  asmTypeTable.put(Type.DOUBLE, IRT.BasicType.DOUBLE)
  asmTypeTable.put(Type.BOOLEAN, IRT.BasicType.BOOLEAN)
  asmTypeTable.put(Type.VOID, IRT.BasicType.VOID)

  c2t.put(classOf[Byte], IRT.BasicType.BYTE)
  c2t.put(classOf[Short], IRT.BasicType.SHORT)
  c2t.put(classOf[Char], IRT.BasicType.CHAR)
  c2t.put(classOf[Int], IRT.BasicType.INT)
  c2t.put(classOf[Long], IRT.BasicType.LONG)
  c2t.put(classOf[Float], IRT.BasicType.FLOAT)
  c2t.put(classOf[Double], IRT.BasicType.DOUBLE)
  c2t.put(classOf[Boolean], IRT.BasicType.BOOLEAN)
  c2t.put(classOf[Unit], IRT.BasicType.VOID)
}

class OnionTypeConversion(table: ClassTable) {
  import OnionTypeConversion._

  def toOnionType(klass: Class[_]): IRT.Type = {
    val returnType: IRT.Type = c2t.get(klass).asInstanceOf[IRT.Type]
    if (returnType != null) return returnType
    if (!klass.isArray) {
      val symbol: IRT.ClassType = table.load(klass.getName)
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
      val componentType: IRT.Type = toOnionType(component)
      return table.loadArray(componentType, dimension)
    }
    null
  }

  def toOnionType(asmType: Type): IRT.Type = {
    asmType.getSort match {
      case Type.VOID => IRT.BasicType.VOID
      case Type.BOOLEAN => IRT.BasicType.BOOLEAN
      case Type.BYTE => IRT.BasicType.BYTE
      case Type.SHORT => IRT.BasicType.SHORT
      case Type.CHAR => IRT.BasicType.CHAR
      case Type.INT => IRT.BasicType.INT
      case Type.LONG => IRT.BasicType.LONG
      case Type.FLOAT => IRT.BasicType.FLOAT
      case Type.DOUBLE => IRT.BasicType.DOUBLE
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