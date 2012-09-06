/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.       *
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
 *         Date: 2005/06/28
 */
object TypeConversion {
  private final val basicTypeTable: Map[BasicType, IRT.BasicTypeRef] = new HashMap[BasicType, IRT.BasicTypeRef]
  private final val c2t: Map[Class[_], IRT.BasicTypeRef] = new HashMap[Class[_], IRT.BasicTypeRef]

  basicTypeTable.put(Type.BYTE, IRT.BasicTypeRef.BYTE)
  basicTypeTable.put(Type.SHORT, IRT.BasicTypeRef.SHORT)
  basicTypeTable.put(Type.CHAR, IRT.BasicTypeRef.CHAR)
  basicTypeTable.put(Type.INT, IRT.BasicTypeRef.INT)
  basicTypeTable.put(Type.LONG, IRT.BasicTypeRef.LONG)
  basicTypeTable.put(Type.FLOAT, IRT.BasicTypeRef.FLOAT)
  basicTypeTable.put(Type.DOUBLE, IRT.BasicTypeRef.DOUBLE)
  basicTypeTable.put(Type.BOOLEAN, IRT.BasicTypeRef.BOOLEAN)
  basicTypeTable.put(Type.VOID, IRT.BasicTypeRef.VOID)

  c2t.put(classOf[Byte], IRT.BasicTypeRef.BYTE)
  c2t.put(classOf[Short], IRT.BasicTypeRef.SHORT)
  c2t.put(classOf[Char], IRT.BasicTypeRef.CHAR)
  c2t.put(classOf[Int], IRT.BasicTypeRef.INT)
  c2t.put(classOf[Long], IRT.BasicTypeRef.LONG)
  c2t.put(classOf[Float], IRT.BasicTypeRef.FLOAT)
  c2t.put(classOf[Double], IRT.BasicTypeRef.DOUBLE)
  c2t.put(classOf[Boolean], IRT.BasicTypeRef.BOOLEAN)
  c2t.put(classOf[Unit], IRT.BasicTypeRef.VOID)
}

class TypeConversion(table: ClassTable) {
  import TypeConversion._

  def toOnionType(klass: Class[_]): IRT.TypeRef = {
    val returnType: IRT.TypeRef = c2t.get(klass).asInstanceOf[IRT.TypeRef]
    if (returnType != null) return returnType
    if (!klass.isArray) {
      val symbol: IRT.ClassTypeRef = table.load(klass.getName)
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
      val componentType: IRT.TypeRef = toOnionType(component)
      return table.loadArray(componentType, dimension)
    }
    return null
  }

  def toOnionType(`type`: Type): IRT.TypeRef = {
    val returnType: IRT.TypeRef = basicTypeTable.get(`type`).asInstanceOf[IRT.TypeRef]
    if (returnType != null) return returnType
    if (`type`.isInstanceOf[ObjectType]) {
      val objType: ObjectType = `type`.asInstanceOf[ObjectType]
      val symbol: IRT.ClassTypeRef = table.load(objType.getClassName)
      if (symbol != null) {
        return symbol
      }
      else {
        return null
      }
    }
    if (`type`.isInstanceOf[ArrayType]) {
      val arrType: ArrayType = `type`.asInstanceOf[ArrayType]
      val component: IRT.TypeRef = toOnionType(arrType.getBasicType)
      if (component != null) {
        return table.loadArray(component, arrType.getDimensions)
      }
    }
    return null
  }
}