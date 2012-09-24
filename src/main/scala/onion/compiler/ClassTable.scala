/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import environment.{ReflectionalClassTypeRef, ClassFileTable, ClassFileClassTypeRef}
import java.util._
import onion.compiler.environment.ClassFileClassTypeRef
import onion.compiler.environment.ClassFileTable
import onion.compiler.environment.ReflectionalClassTypeRef
import onion.compiler.toolbox.Strings
import org.apache.bcel.classfile.JavaClass

/**
 * @author Kota Mizushima
 * Date: 2005/06/22
 */
class ClassTable(private val classPath: String) {
  val classes = new OrderedTable[IRT.ClassDefinition]
  private val classFiles = new HashMap[String, IRT.ClassTypeRef]
  private val arrayClasses = new HashMap[String, IRT.ArrayTypeRef]
  private val table = new ClassFileTable(classPath)

  def loadArray(component: IRT.TypeRef, dimension: Int): IRT.ArrayTypeRef = {
    val arrayName = Strings.repeat("[", dimension) + component.name
    var array: IRT.ArrayTypeRef = arrayClasses.get(arrayName)
    if (array != null) return array
    array = new IRT.ArrayTypeRef(component, dimension, this)
    arrayClasses.put(arrayName, array)
    array
  }

  def load(className: String): IRT.ClassTypeRef = {
    var clazz: IRT.ClassTypeRef = lookup(className)
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

  def rootClass: IRT.ClassTypeRef = load("java.lang.Object")

  def lookup(className: String): IRT.ClassTypeRef = {
    val ref = classes.get(className)
    if ((ref != null)) ref else classFiles.get(className)
  }

}