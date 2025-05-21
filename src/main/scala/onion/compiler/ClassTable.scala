package onion.compiler

import java.util.{HashMap => JHashMap}
import onion.compiler.environment.AsmRefs.AsmClassType
import onion.compiler.environment.ClassFileTable
import onion.compiler.environment.ReflectionRefs.ReflectClassType

class ClassTable(classPath: String):
  val classes = new OrderedTable[IRT.ClassDefinition]
  private val classFiles = new JHashMap[String, IRT.ClassType]
  private val arrayClasses = new JHashMap[String, IRT.ArrayType]
  private val table = new ClassFileTable(classPath)

  def loadArray(component: IRT.Type, dimension: Int): IRT.ArrayType =
    val arrayName = "[" * dimension + component.name
    var array = arrayClasses.get(arrayName)
    if array != null then return array
    array = IRT.ArrayType(component, dimension, this)
    arrayClasses.put(arrayName, array)
    array

  def load(className: String): IRT.ClassType =
    var clazz = lookup(className)
    if clazz == null then
      val bytes = table.loadBytes(className)
      if bytes != null then
        clazz = new AsmClassType(bytes, this)
        classFiles.put(clazz.name, clazz)
      else
        try
          clazz = new ReflectClassType(Class.forName(className, true, Thread.currentThread.getContextClassLoader), this)
          classFiles.put(clazz.name, clazz)
        catch
          case _: ClassNotFoundException => ()
    clazz

  def rootClass: IRT.ClassType = load("java.lang.Object")

  def lookup(className: String): IRT.ClassType =
    classes.get(className) match
      case Some(ref) => ref
      case None => classFiles.get(className)

