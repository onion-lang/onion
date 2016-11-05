/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import onion.compiler._
import onion.compiler.Modifier
import org.apache.bcel.Constants._
import org.apache.bcel.classfile._
import org.apache.bcel.generic.Type

import scala.collection.mutable

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

class ClassFileClassTypeRef(javaClass: JavaClass, table: ClassTable) extends IRT.AbstractClassType {

  import ClassFileClassTypeRef._

  private val bridge    : OnionTypeConversion = new OnionTypeConversion(table)
  private val modifier_ : Int                 = toOnionModifier(javaClass.getModifiers)


  private lazy val methods_ : MultiTable[IRT.Method] = {
    val methods = new MultiTable[IRT.Method]
    for (method <- javaClass.getMethods) {
      if (!(method.getName == CONSTRUCTOR_NAME)) methods.add(translate(method))
    }
    methods
  }


  private lazy val fields_ : OrderedTable[IRT.FieldRef] = {
    val fields = new OrderedTable[IRT.FieldRef]
    for (field <- javaClass.getFields) {
      fields.add(translate(field))
    }
    fields
  }

  private lazy val constructors_ : Seq[IRT.ConstructorRef] = {
    val constructors = mutable.Buffer[IRT.ConstructorRef]()
    for (method <- javaClass.getMethods) {
      if (method.getName == CONSTRUCTOR_NAME) {
        constructors += translateConstructor(method)
      }
    }
    constructors
  }

  def isInterface: Boolean = (javaClass.getModifiers & ACC_INTERFACE) != 0

  def modifier: Int = modifier_

  def name: String = javaClass.getClassName

  def superClass: IRT.ClassType = {
    val superClass: IRT.ClassType = table.load(javaClass.getSuperclassName)
    if (superClass eq this) {
      return null
    }
    superClass
  }

  override def interfaces: Seq[IRT.ClassType] = {
    val interfaceNames: Seq[String] = javaClass.getInterfaceNames
    val interfaces: Array[IRT.ClassType] = new Array[IRT.ClassType](interfaceNames.length)
    for(i <- interfaces.indices) {
      interfaces(i) = table.load(interfaceNames(i))
    }
    interfaces
  }

  def methods: Seq[IRT.Method] =  methods_.values

  def methods(name: String): Array[IRT.Method] =  methods_.get(name).toArray

  def fields: Array[IRT.FieldRef] =  fields_.values.toArray

  def field(name: String): IRT.FieldRef = fields_.get(name).getOrElse(null)

  def constructors: Array[IRT.ConstructorRef] = {
    constructors_.toArray
  }

  private def translate(method: Method): IRT.Method = {
    val arguments: Array[Type] = method.getArgumentTypes
    val argumentSymbols: Array[IRT.Type] = new Array[IRT.Type](arguments.length)
    for(i <- 0 until arguments.length) {
      argumentSymbols(i) = bridge.toOnionType(arguments(i))
    }
    val returnSymbol: IRT.Type = bridge.toOnionType(method.getReturnType)
    new ClassFileMethodRef(toOnionModifier(method.getModifiers), this, method.getName, argumentSymbols, returnSymbol)
  }

  private def translate(field: Field): IRT.FieldRef = {
    val symbol: IRT.Type = bridge.toOnionType(field.getType)
    new ClassFileFieldRef(toOnionModifier(field.getModifiers), this, field.getName, symbol)
  }

  private def translateConstructor(method: Method): IRT.ConstructorRef = {
    val arguments: Array[Type] = method.getArgumentTypes
    val argumentSymbols: Array[IRT.Type] = new Array[IRT.Type](arguments.length)
    for(i <- 0 until arguments.length) {
      argumentSymbols(i) = bridge.toOnionType(arguments(i))
    }
    new ClassFileConstructorRef(toOnionModifier(method.getModifiers), this, method.getName, argumentSymbols)
  }
}