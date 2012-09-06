/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.       *
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
 *         Date: 2006/1/10
 */
object ReflectionalClassTypeRef {
  private def toOnionModifier(src: Int): Int = {
    var modifier: Int = 0
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.PRIVATE)) Modifier.PRIVATE else modifier)
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.PROTECTED)) Modifier.PROTECTED else modifier)
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.PUBLIC)) Modifier.PUBLIC else modifier)
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.STATIC)) Modifier.STATIC else modifier)
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.SYNCHRONIZED)) Modifier.SYNCHRONIZED else modifier)
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.ABSTRACT)) Modifier.ABSTRACT else modifier)
    modifier |= (if (isOn(src, java.lang.reflect.Modifier.FINAL)) Modifier.FINAL else modifier)
    return modifier
  }

  private def isOn(modifier: Int, flag: Int): Boolean = {
    return (modifier & flag) != 0
  }

  private final val CONSTRUCTOR_NAME: String = "<init>"
}

class ReflectionalClassTypeRef(klass: Class[_], table: ClassTable) extends IRT.AbstractClassTypeRef {
  import ReflectionalClassTypeRef._
  private val bridge: OnionTypeBridge = new OnionTypeBridge(table)
  private val modifier_ : Int = toOnionModifier(klass.getModifiers)
  private var methods_ : MultiTable[IRT.MethodRef] = null
  private var fields_ : OrderedTable[IRT.FieldRef] = null
  private var constructors_ : List[IRT.ConstructorRef] = null

  def isInterface: Boolean = {
    return (klass.getModifiers & java.lang.reflect.Modifier.INTERFACE) != 0
  }

  def modifier: Int = modifier_

  def name: String = klass.getName

  def superClass: IRT.ClassTypeRef = {
    val superKlass: Class[_] = klass.getSuperclass
    if (superKlass == null) return table.rootClass
    val superClass: IRT.ClassTypeRef = table.load(superKlass.getName)
    if (superClass eq this) return null
    superClass
  }

  def interfaces: Array[IRT.ClassTypeRef] = {
    val interfaces: Array[Class[_]] = klass.getInterfaces
    val interfaceSyms: Array[IRT.ClassTypeRef] = new Array[IRT.ClassTypeRef](interfaces.length)
    var i: Int = 0
    while (i < interfaces.length) {
      interfaceSyms(i) = table.load(interfaces(i).getName)
      i += 1;
    }
    interfaceSyms
  }

  def methods: Array[IRT.MethodRef] = {
    requireMethodTable
    methods_.values.toArray(new Array[IRT.MethodRef](0))
  }

  def methods(name: String): Array[IRT.MethodRef] = {
    requireMethodTable
    methods_.get(name).toArray(new Array[IRT.MethodRef](0))
  }

  private def requireMethodTable {
    if (methods_ == null) {
      methods_ = new MultiTable[IRT.MethodRef]
      for (method <- klass.getMethods) {
        if (!(method.getName == CONSTRUCTOR_NAME)) {
          methods_.add(translate(method))
        }
      }
    }
  }

  def fields: Array[IRT.FieldRef] = {
    requireFieldTable
    fields_.values.toArray(new Array[IRT.FieldRef](0))
  }

  def field(name: String): IRT.FieldRef = {
    requireFieldTable
    fields_.get(name)
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

  private def translate(method: Method): IRT.MethodRef = {
    val arguments: Array[Class[_]] = method.getParameterTypes
    val argumentRefs: Array[IRT.TypeRef] = new Array[IRT.TypeRef](arguments.length)
    var i: Int = 0
    while (i < arguments.length) {
      argumentRefs(i) = bridge.toOnionType(arguments(i))
      i += 1;
    }
    val returnRef: IRT.TypeRef = bridge.toOnionType(method.getReturnType)
    new ClassFileMethodRef(toOnionModifier(method.getModifiers), this, method.getName, argumentRefs, returnRef)
  }

  private def translate(field: Field): IRT.FieldRef = {
    return new ClassFileFieldRef(toOnionModifier(field.getModifiers), this, field.getName, bridge.toOnionType(field.getType))
  }

  private def translate(constructor: Constructor[_]): IRT.ConstructorRef = {
    val arguments: Array[Class[_]] = constructor.getParameterTypes
    val argumentRefs: Array[IRT.TypeRef] = new Array[IRT.TypeRef](arguments.length)
    var i: Int = 0
    while (i < arguments.length) {
      argumentRefs(i) = bridge.toOnionType(arguments(i))
      i += 1;
    }
    new ClassFileConstructorRef(toOnionModifier(constructor.getModifiers), this, "<init>", argumentRefs)
  }
}