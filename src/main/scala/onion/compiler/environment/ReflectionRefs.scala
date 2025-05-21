/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import onion.compiler.{IRT, Modifier, OnionTypeConversion, MultiTable, OrderedTable, ClassTable}
import java.lang.reflect.{Constructor, Field, Method}
import java.util.{ArrayList, List}

object ReflectionRefs {
  private def toOnionModifier(src: Int): Int = {
    ((if ((src & java.lang.reflect.Modifier.PRIVATE) != 0) Modifier.PRIVATE else 0)
      | (if ((src & java.lang.reflect.Modifier.PROTECTED) != 0) Modifier.PROTECTED else 0)
      | (if ((src & java.lang.reflect.Modifier.PUBLIC) != 0) Modifier.PUBLIC else 0)
      | (if ((src & java.lang.reflect.Modifier.STATIC) != 0) Modifier.STATIC else 0)
      | (if ((src & java.lang.reflect.Modifier.SYNCHRONIZED) != 0) Modifier.SYNCHRONIZED else 0)
      | (if ((src & java.lang.reflect.Modifier.ABSTRACT) != 0) Modifier.ABSTRACT else 0)
      | (if ((src & java.lang.reflect.Modifier.FINAL) != 0) Modifier.FINAL else 0))
  }

  final val CONSTRUCTOR_NAME = "<init>"

  class ReflectMethodRef(method: Method, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.Method {
    override val modifier: Int = toOnionModifier(method.getModifiers)
    override val name: String = method.getName
    private val argTypes: Array[IRT.Type] = method.getParameterTypes.map(t => bridge.toOnionType(t))
    override def arguments: Array[IRT.Type] = argTypes.clone()
    override val returnType: IRT.Type = bridge.toOnionType(method.getReturnType)
    val underlying: Method = method
  }

  class ReflectFieldRef(field: Field, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.FieldRef {
    override val modifier: Int = toOnionModifier(field.getModifiers)
    override val name: String = field.getName
    override val `type`: IRT.Type = bridge.toOnionType(field.getType)
    val underlying: Field = field
  }

  class ReflectConstructorRef(constructor: Constructor[_], override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.ConstructorRef {
    override val modifier: Int = toOnionModifier(constructor.getModifiers)
    override val name: String = "<init>"
    private val args0: Array[IRT.Type] = constructor.getParameterTypes.map(t => bridge.toOnionType(t))
    override def getArgs: Array[IRT.Type] = args0.clone()
    val underlying: Constructor[_] = constructor
  }

  class ReflectClassType(klass: Class[_], table: ClassTable) extends IRT.AbstractClassType {
    private val bridge: OnionTypeConversion              = new OnionTypeConversion(table)
    private val modifier_ : Int                          = toOnionModifier(klass.getModifiers)
    private var methods_ : MultiTable[IRT.Method]        = _
    private var fields_ : OrderedTable[IRT.FieldRef]     = _
    private var constructors_ : List[IRT.ConstructorRef] = _

    def isInterface: Boolean = (klass.getModifiers & java.lang.reflect.Modifier.INTERFACE) != 0

    def modifier: Int = modifier_

    def name: String = klass.getName

    def superClass: IRT.ClassType = {
      val superKlass: Class[_] = klass.getSuperclass
      if (superKlass == null) return table.rootClass
      val superClass: IRT.ClassType = table.load(superKlass.getName)
      if (superClass eq this) null else superClass
    }

    def interfaces: Seq[IRT.ClassType] = {
      val interfacesArr: Array[Class[_]] = klass.getInterfaces
      val interfaceSyms: Array[IRT.ClassType] = new Array[IRT.ClassType](interfacesArr.length)
      for(i <- interfacesArr.indices) {
        interfaceSyms(i) = table.load(interfacesArr(i).getName)
      }
      interfaceSyms.toIndexedSeq
    }

    def methods: Seq[IRT.Method] = {
      requireMethodTable()
      methods_.values
    }

    def methods(name: String): Array[IRT.Method] = {
      requireMethodTable()
      methods_.get(name).toArray
    }

    private def requireMethodTable(): Unit = {
      if (methods_ == null) {
        methods_ = new MultiTable[IRT.Method]
        for (method <- klass.getMethods if method.getName != CONSTRUCTOR_NAME) {
          methods_.add(new ReflectMethodRef(method, this, bridge))
        }
      }
    }

    def fields: Array[IRT.FieldRef] = {
      requireFieldTable()
      fields_.values.toArray
    }

    def field(name: String): IRT.FieldRef = {
      requireFieldTable()
      fields_.get(name).orNull
    }

    private def requireFieldTable(): Unit = {
      if (fields_ == null) {
        fields_ = new OrderedTable[IRT.FieldRef]
        for (field <- klass.getFields) {
          fields_.add(new ReflectFieldRef(field, this, bridge))
        }
      }
    }

    def constructors: Array[IRT.ConstructorRef] = {
      if (constructors_ == null) {
        constructors_ = new ArrayList[IRT.ConstructorRef]
        for (ctor <- klass.getConstructors) {
          constructors_.add(new ReflectConstructorRef(ctor, this, bridge))
        }
      }
      constructors_.toArray(new Array[IRT.ConstructorRef](0))
    }
  }
}
