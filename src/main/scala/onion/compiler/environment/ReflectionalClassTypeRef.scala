/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.List
import onion.compiler._
import onion.compiler.Modifier

/**
 * @author Kota Mizushima
 *
 */
object ReflectionalClassTypeRef {
  private def toOnionModifier(src: Int): Int = {
    ( (if (isOn(src, java.lang.reflect.Modifier.PRIVATE)) Modifier.PRIVATE else 0)
    | (if (isOn(src, java.lang.reflect.Modifier.PROTECTED)) Modifier.PROTECTED else 0)
    | (if (isOn(src, java.lang.reflect.Modifier.PUBLIC)) Modifier.PUBLIC else 0)
    | (if (isOn(src, java.lang.reflect.Modifier.STATIC)) Modifier.STATIC else 0)
    | (if (isOn(src, java.lang.reflect.Modifier.SYNCHRONIZED)) Modifier.SYNCHRONIZED else 0)
    | (if (isOn(src, java.lang.reflect.Modifier.ABSTRACT)) Modifier.ABSTRACT else 0)
    | (if (isOn(src, java.lang.reflect.Modifier.FINAL)) Modifier.FINAL else 0))
  }

  private def isOn(modifier: Int, flag: Int): Boolean =  (modifier & flag) != 0

  private final val CONSTRUCTOR_NAME: String = "<init>"
}

class ReflectionalClassTypeRef(klass: Class[_], table: ClassTable) extends IRT.AbstractClassType {
  import ReflectionalClassTypeRef._
  private val bridge: OnionTypeConversion              = new OnionTypeConversion(table)
  private val modifier_ : Int                          = toOnionModifier(klass.getModifiers)
  private var methods_ : MultiTable[IRT.Method]        = null
  private var fields_ : OrderedTable[IRT.FieldRef]     = null
  private var constructors_ : List[IRT.ConstructorRef] = null

  def isInterface: Boolean = (klass.getModifiers & java.lang.reflect.Modifier.INTERFACE) != 0

  def modifier: Int = modifier_

  def name: String = klass.getName

  def superClass: IRT.ClassType = {
    val superKlass: Class[_] = klass.getSuperclass
    if (superKlass == null) return table.rootClass
    val superClass: IRT.ClassType = table.load(superKlass.getName)
    if (superClass eq this) return null
    superClass
  }

  def interfaces: Array[IRT.ClassType] = {
    val interfaces: Array[Class[_]] = klass.getInterfaces
    val interfaceSyms: Array[IRT.ClassType] = new Array[IRT.ClassType](interfaces.length)
    for(i <- 0 until interfaces.length) {
      interfaceSyms(i) = table.load(interfaces(i).getName)
    }
    interfaceSyms
  }

  def methods: Array[IRT.Method] = {
    requireMethodTable
    methods_.values.toArray
  }

  def methods(name: String): Array[IRT.Method] = {
    requireMethodTable
    methods_.get(name).toArray
  }

  private def requireMethodTable {
    if (methods_ == null) {
      methods_ = new MultiTable[IRT.Method]
      for (method <- klass.getMethods) {
        if (!(method.getName == CONSTRUCTOR_NAME)) {
          methods_.add(translate(method))
        }
      }
    }
  }

  def fields: Array[IRT.FieldRef] = {
    requireFieldTable
    fields_.values.toArray
  }

  def field(name: String): IRT.FieldRef = {
    requireFieldTable
    fields_.get(name).getOrElse(null)
  }

  private def requireFieldTable {
    if (fields == null) {
      fields_ = new OrderedTable[IRT.FieldRef]
      for (field <- klass.getFields) {
        fields_.add(translate(field))
      }
    }
  }

  def constructors: Array[IRT.ConstructorRef] = {
    if (constructors_ == null) {
      constructors_ = new ArrayList[IRT.ConstructorRef]
      for (method <- klass.getConstructors) {
        constructors_.add(translate(method))
      }
    }
    constructors_.toArray(new Array[IRT.ConstructorRef](0))
  }

  private def translate(method: Method): IRT.Method = {
    val arguments: Array[Class[_]] = method.getParameterTypes
    val argumentRefs: Array[IRT.Type] = new Array[IRT.Type](arguments.length)
    for(i <- 0 until arguments.length) {
      argumentRefs(i) = bridge.toOnionType(arguments(i))
    }
    val returnRef: IRT.Type = bridge.toOnionType(method.getReturnType)
    new ClassFileMethodRef(toOnionModifier(method.getModifiers), this, method.getName, argumentRefs, returnRef)
  }

  private def translate(field: Field): IRT.FieldRef = {
    new ClassFileFieldRef(toOnionModifier(field.getModifiers), this, field.getName, bridge.toOnionType(field.getType))
  }

  private def translate(constructor: Constructor[_]): IRT.ConstructorRef = {
    val arguments: Array[Class[_]] = constructor.getParameterTypes
    val argumentRefs: Array[IRT.Type] = new Array[IRT.Type](arguments.length)
    for(i <- 0 until arguments.length) {
      argumentRefs(i) = bridge.toOnionType(arguments(i))
    }
    new ClassFileConstructorRef(toOnionModifier(constructor.getModifiers), this, "<init>", argumentRefs)
  }
}