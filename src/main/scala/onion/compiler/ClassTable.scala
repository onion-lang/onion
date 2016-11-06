/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.{HashMap => JHashMap}
import onion.compiler.environment.ClassFileClassTypeRef
import onion.compiler.environment.ClassFileTable
import onion.compiler.environment.ReflectionalClassTypeRef

/**
 * @author Kota Mizushima
 *
 */
class ClassTable(classPath: String) {
  val classes = new OrderedTable[IRT.ClassDefinition]
  private val classFiles = new JHashMap[String, IRT.ClassType]
  private val arrayClasses = new JHashMap[String, IRT.ArrayType]
  private val table = new ClassFileTable(classPath)

  def loadArray(component: IRT.Type, dimension: Int): IRT.ArrayType = {
    val arrayName = "[" * dimension + component.name
    var array: IRT.ArrayType = arrayClasses.get(arrayName)
    if (array != null) return array
    array = new IRT.ArrayType(component, dimension, this)
    arrayClasses.put(arrayName, array)
    array
  }

  def load(className: String): IRT.ClassType = {
    var clazz: IRT.ClassType = lookup(className)
    if (clazz == null) {
      val javaClass = table.load(className)
      if (javaClass != null) {
        clazz = new ClassFileClassTypeRef(javaClass, this)
        classFiles.put(clazz.name, clazz)
      } else {
        try {
          clazz = new ReflectionalClassTypeRef(Class.forName(className, true, Thread.currentThread.getContextClassLoader), this)
          classFiles.put(clazz.name, clazz)
        }
        catch {
          case e: ClassNotFoundException => {}
        }
      }
    }
    clazz
  }

  def rootClass: IRT.ClassType = load("java.lang.Object")

  def lookup(className: String): IRT.ClassType = {
    classes.get(className) match {
      case Some(ref) => ref
      case None => classFiles.get(className)
    }
  }

}