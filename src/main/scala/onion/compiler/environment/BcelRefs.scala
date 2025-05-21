/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import onion.compiler.{IRT, Modifier, OnionTypeConversion, MultiTable, OrderedTable, ClassTable}
import org.apache.bcel.classfile.{Field, JavaClass, Method}
import org.apache.bcel.Constants._
import org.apache.bcel.generic.Type

object BcelRefs {
  private def toOnionModifier(src: Int): Int = {
    var modifier: Int = 0
    modifier |= (if ((src & ACC_PRIVATE) != 0) Modifier.PRIVATE else modifier)
    modifier |= (if ((src & ACC_PROTECTED) != 0) Modifier.PROTECTED else modifier)
    modifier |= (if ((src & ACC_PUBLIC) != 0) Modifier.PUBLIC else modifier)
    modifier |= (if ((src & ACC_STATIC) != 0) Modifier.STATIC else modifier)
    modifier |= (if ((src & ACC_SYNCHRONIZED) != 0) Modifier.SYNCHRONIZED else modifier)
    modifier |= (if ((src & ACC_ABSTRACT) != 0) Modifier.ABSTRACT else modifier)
    modifier |= (if ((src & ACC_FINAL) != 0) Modifier.FINAL else modifier)
    modifier
  }

  final val CONSTRUCTOR_NAME = "<init>"

  class BcelMethodRef(method: Method, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.Method {
    override val modifier: Int = toOnionModifier(method.getModifiers)
    override val name: String = method.getName
    private val argTypes: Array[IRT.Type] = method.getArgumentTypes.map(t => bridge.toOnionType(t))
    override def arguments: Array[IRT.Type] = argTypes.clone()
    override val returnType: IRT.Type = bridge.toOnionType(method.getReturnType)
    val underlying: Method = method
  }

  class BcelFieldRef(field: Field, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.FieldRef {
    override val modifier: Int = toOnionModifier(field.getModifiers)
    override val name: String = field.getName
    override val `type`: IRT.Type = bridge.toOnionType(field.getType)
    val underlying: Field = field
  }

  class BcelConstructorRef(method: Method, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.ConstructorRef {
    override val modifier: Int = toOnionModifier(method.getModifiers)
    override val name: String = method.getName
    private val args0: Array[IRT.Type] = method.getArgumentTypes.map(t => bridge.toOnionType(t))
    override def getArgs: Array[IRT.Type] = args0.clone()
    val underlying: Method = method
  }

  class BcelClassType(javaClass: JavaClass, table: ClassTable) extends IRT.AbstractClassType {
    import scala.collection.mutable

    private val bridge    : OnionTypeConversion = new OnionTypeConversion(table)
    private val modifier_ : Int                 = toOnionModifier(javaClass.getModifiers)

    private lazy val methods_ : MultiTable[IRT.Method] = {
      val methods = new MultiTable[IRT.Method]
      for (method <- javaClass.getMethods if method.getName != CONSTRUCTOR_NAME) {
        methods.add(new BcelMethodRef(method, this, bridge))
      }
      methods
    }

    private lazy val fields_ : OrderedTable[IRT.FieldRef] = {
      val fields = new OrderedTable[IRT.FieldRef]
      for (field <- javaClass.getFields) {
        fields.add(new BcelFieldRef(field, this, bridge))
      }
      fields
    }

    private lazy val constructors_ : Seq[IRT.ConstructorRef] = {
      val ctors = mutable.Buffer[IRT.ConstructorRef]()
      for (method <- javaClass.getMethods if method.getName == CONSTRUCTOR_NAME) {
        ctors += new BcelConstructorRef(method, this, bridge)
      }
      ctors.toSeq
    }

    def isInterface: Boolean = (javaClass.getModifiers & ACC_INTERFACE) != 0

    def modifier: Int = modifier_

    def name: String = javaClass.getClassName

    def superClass: IRT.ClassType = {
      val superClass: IRT.ClassType = table.load(javaClass.getSuperclassName)
      if (superClass eq this) null else superClass
    }

    override def interfaces: Seq[IRT.ClassType] = {
      val names = javaClass.getInterfaceNames.toIndexedSeq
      val interfaces = new Array[IRT.ClassType](names.length)
      for (i <- interfaces.indices) {
        interfaces(i) = table.load(names(i))
      }
      interfaces.toIndexedSeq
    }

    def methods: Seq[IRT.Method] = methods_.values

    def methods(name: String): Array[IRT.Method] = methods_.get(name).toArray

    def fields: Array[IRT.FieldRef] = fields_.values.toArray

    def field(name: String): IRT.FieldRef = fields_.get(name).orNull

    def constructors: Array[IRT.ConstructorRef] = constructors_.toArray
  }
}
