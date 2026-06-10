/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.{HashMap => JHashMap}
import onion.compiler.environment.AsmRefs.AsmClassType
import onion.compiler.environment.ClassFileTable
import onion.compiler.environment.ReflectionRefs.ReflectClassType

/**
 * @author Kota Mizushima
 *
 */
class ClassTable(classPath: String) {
  val classes = new OrderedTable[TypedAST.ClassDefinition]
  private val classFiles = new JHashMap[String, TypedAST.ClassType]
  private val arrayClasses = new JHashMap[String, TypedAST.ArrayType]
  private val missingClasses = new java.util.HashSet[String]
  private val table = new ClassFileTable(classPath)

  def loadArray(component: TypedAST.Type, dimension: Int): TypedAST.ArrayType = {
    val arrayName = "[" * dimension + component.name
    var array: TypedAST.ArrayType = arrayClasses.get(arrayName)
    if (array != null) return array
    array = new TypedAST.ArrayType(component, dimension, this)
    arrayClasses.put(arrayName, array)
    array
  }

  /**
   * Null-returning lookup kept for the bytecode/reflection environment internals,
   * where absent classes flow through legacy null-tolerant resolution paths.
   * Prefer [[load]] (Option) or [[loadRequired]] elsewhere.
   */
  def loadOrNull(className: String): TypedAST.ClassType = {
    var clazz: TypedAST.ClassType = lookup(className)
    if (clazz == null && missingClasses.contains(className)) return null
    if (clazz == null) {
      val bytes = table.loadBytes(className)
      if (bytes != null) {
        clazz = new AsmClassType(bytes, this)
        classFiles.put(clazz.name, clazz)
      } else {
        try {
          clazz = new ReflectClassType(Class.forName(className, false, Thread.currentThread.getContextClassLoader), this)
          classFiles.put(clazz.name, clazz)
        }
        catch {
          case _: ClassNotFoundException =>
            missingClasses.add(className)
        }
      }
    }
    clazz
  }

  def load(className: String): Option[TypedAST.ClassType] = Option(loadOrNull(className))

  /** Loads a class that the compiler itself depends on (JDK / onion runtime). */
  def loadRequired(className: String): TypedAST.ClassType =
    loadOrNull(className) match {
      case null => throw new IllegalStateException(s"required class is not on the compiler classpath: $className")
      case clazz => clazz
    }

  def rootClass: TypedAST.ClassType = loadRequired("java.lang.Object")

  def lookup(className: String): TypedAST.ClassType = {
    classes.get(className) match {
      case Some(ref) => ref
      case None => classFiles.get(className)
    }
  }

  /** Option-returning version of lookup for safer null handling */
  def lookupOpt(className: String): Option[TypedAST.ClassType] = Option(lookup(className))

}
