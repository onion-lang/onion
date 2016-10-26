/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.HashMap
import java.util.Map
import org.apache.bcel.generic.ArrayType
import org.apache.bcel.generic.BasicType
import org.apache.bcel.generic.ObjectType
import org.apache.bcel.generic.Type

/**
 * @author Kota Mizushima
 *
 */
object OnionTypeConversion {
  private final val basicTypeTable: Map[BasicType, IRT.BasicType] = new HashMap[BasicType, IRT.BasicType]
  private final val c2t: Map[Class[_], IRT.BasicType]             = new HashMap[Class[_], IRT.BasicType]

  basicTypeTable.put(Type.BYTE, IRT.BasicType.BYTE)
  basicTypeTable.put(Type.SHORT, IRT.BasicType.SHORT)
  basicTypeTable.put(Type.CHAR, IRT.BasicType.CHAR)
  basicTypeTable.put(Type.INT, IRT.BasicType.INT)
  basicTypeTable.put(Type.LONG, IRT.BasicType.LONG)
  basicTypeTable.put(Type.FLOAT, IRT.BasicType.FLOAT)
  basicTypeTable.put(Type.DOUBLE, IRT.BasicType.DOUBLE)
  basicTypeTable.put(Type.BOOLEAN, IRT.BasicType.BOOLEAN)
  basicTypeTable.put(Type.VOID, IRT.BasicType.VOID)

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
      var component: Class[_] = null
      do {
        dimension += 1
        component = component.getComponentType
      } while (component.getComponentType != null)
      val componentType: IRT.Type = toOnionType(component)
      return table.loadArray(componentType, dimension)
    }
    null
  }

  def toOnionType(`type`: Type): IRT.Type = {
    val returnType: IRT.Type = basicTypeTable.get(`type`).asInstanceOf[IRT.Type]
    if (returnType != null) return returnType
    if (`type`.isInstanceOf[ObjectType]) {
      val objType: ObjectType = `type`.asInstanceOf[ObjectType]
      val symbol: IRT.ClassType = table.load(objType.getClassName)
      if (symbol != null) {
        return symbol
      }
      else {
        return null
      }
    }
    if (`type`.isInstanceOf[ArrayType]) {
      val arrType: ArrayType = `type`.asInstanceOf[ArrayType]
      val component: IRT.Type = toOnionType(arrType.getBasicType)
      if (component != null) {
        return table.loadArray(component, arrType.getDimensions)
      }
    }
    null
  }
}