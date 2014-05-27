/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import java.util.ArrayList
import java.util.List
import onion.compiler._
import onion.compiler.Modifier
import org.apache.bcel.Constants._
import org.apache.bcel.classfile._
import org.apache.bcel.generic.Type

/**
 * @author Kota Mizushima
 *
 */
object ClassFileClassTypeRef {
  private def toOnionModifier(src: Int): Int = {
    var modifier: Int = 0
    modifier |= (if (isOn(src, ACC_PRIVATE)) Modifier.PRIVATE else modifier)
    modifier |= (if (isOn(src, ACC_PROTECTED)) Modifier.PROTECTED else modifier)
    modifier |= (if (isOn(src, ACC_PUBLIC)) Modifier.PUBLIC else modifier)
    modifier |= (if (isOn(src, ACC_STATIC)) Modifier.STATIC else modifier)
    modifier |= (if (isOn(src, ACC_SYNCHRONIZED)) Modifier.SYNCHRONIZED else modifier)
    modifier |= (if (isOn(src, ACC_ABSTRACT)) Modifier.ABSTRACT else modifier)
    modifier |= (if (isOn(src, ACC_FINAL)) Modifier.FINAL else modifier)
    modifier
  }

  private def isOn(modifier: Int, flag: Int): Boolean =  (modifier & flag) != 0

  private final val CONSTRUCTOR_NAME: String = "<init>"
}

class ClassFileClassTypeRef(javaClass: JavaClass, table: ClassTable) extends IRT.AbstractClassTypeRef {

  import ClassFileClassTypeRef._

  private var bridge: OnionTypeConversion = new OnionTypeConversion(table)
  private var modifier_ : Int = toOnionModifier(javaClass.getModifiers)
  private var methods_ : MultiTable[IRT.MethodRef] = null
  private var fields_ : OrderedTable[IRT.FieldRef] = null
  private var constructors_ : List[IRT.ConstructorRef] = null

  def isInterface: Boolean = (javaClass.getModifiers & ACC_INTERFACE) != 0

  def modifier: Int = modifier_

  def name: String = javaClass.getClassName

  def superClass: IRT.ClassTypeRef = {
    val superClass: IRT.ClassTypeRef = table.load(javaClass.getSuperclassName)
    if (superClass eq this) {
      return null
    }
    superClass
  }

  def interfaces: Array[IRT.ClassTypeRef] = {
    val interfaceNames: Array[String] = javaClass.getInterfaceNames
    val interfaces: Array[IRT.ClassTypeRef] = new Array[IRT.ClassTypeRef](interfaceNames.length)
    var i: Int = 0
    while (i < interfaces.length) {
      interfaces(i) = table.load(interfaceNames(i))
      i += 1
    }
    interfaces
  }

  def methods: Array[IRT.MethodRef] = {
    requireMethodTable
    methods_.values.toArray
  }

  def methods(name: String): Array[IRT.MethodRef] = {
    requireMethodTable
    methods_.get(name).toArray
  }

  private def requireMethodTable {
    if (methods_ == null) {
      methods_ = new MultiTable[IRT.MethodRef]
      for (method <- javaClass.getMethods) {
        if (!(method.getName == CONSTRUCTOR_NAME)) methods_.add(translate(method))
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
    if (fields_ == null) {
      fields_ = new OrderedTable[IRT.FieldRef]
      for (field <- javaClass.getFields) {
        fields_.add(translate(field))
      }
    }
  }

  def constructors: Array[IRT.ConstructorRef] = {
    if (constructors_ == null) {
      constructors_ = new ArrayList[IRT.ConstructorRef]
      for (method <- javaClass.getMethods) {
        if (method.getName == CONSTRUCTOR_NAME) {
          constructors_.add(translateConstructor(method))
        }
      }
    }
    constructors_.toArray(new Array[IRT.ConstructorRef](0))
  }

  private def translate(method: Method): IRT.MethodRef = {
    val arguments: Array[Type] = method.getArgumentTypes
    val argumentSymbols: Array[IRT.TypeRef] = new Array[IRT.TypeRef](arguments.length)
    var i: Int = 0
    while (i < arguments.length) {
      argumentSymbols(i) = bridge.toOnionType(arguments(i))
      i += 1
    }
    val returnSymbol: IRT.TypeRef = bridge.toOnionType(method.getReturnType)
    new ClassFileMethodRef(toOnionModifier(method.getModifiers), this, method.getName, argumentSymbols, returnSymbol)
  }

  private def translate(field: Field): IRT.FieldRef = {
    val symbol: IRT.TypeRef = bridge.toOnionType(field.getType)
    new ClassFileFieldRef(toOnionModifier(field.getModifiers), this, field.getName, symbol)
  }

  private def translateConstructor(method: Method): IRT.ConstructorRef = {
    val arguments: Array[Type] = method.getArgumentTypes
    val argumentSymbols: Array[IRT.TypeRef] = new Array[IRT.TypeRef](arguments.length)
    var i: Int = 0
    while (i < arguments.length) {
      argumentSymbols(i) = bridge.toOnionType(arguments(i))
      i += 1
    }
    new ClassFileConstructorRef(toOnionModifier(method.getModifiers), this, method.getName, argumentSymbols)
  }
}