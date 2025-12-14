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
import java.lang.reflect.{GenericArrayType, ParameterizedType, Type, TypeVariable, WildcardType}
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

  private final class GenericTypeMapper(table: ClassTable) {
    private val bridge = new OnionTypeConversion(table)
    private val root = table.rootClass

    def typeParamEnv(typeParams: Array[IRT.TypeParameter]): Map[String, IRT.TypeVariableType] = {
      val env = scala.collection.mutable.HashMap[String, IRT.TypeVariableType]()
      var i = 0
      while (i < typeParams.length) {
        val tp = typeParams(i)
        val upper = tp.upperBound match {
          case Some(ap: IRT.AppliedClassType) => ap.raw
          case Some(ct: IRT.ClassType) => ct
          case _ => root
        }
        env += tp.name -> new IRT.TypeVariableType(tp.name, upper)
        i += 1
      }
      env.toMap
    }

    def typeParamsFrom(vars: Array[? <: TypeVariable[?]], baseEnv: Map[String, IRT.TypeVariableType]): (Array[IRT.TypeParameter], Map[String, IRT.TypeVariableType]) = {
      val params = new Array[IRT.TypeParameter](vars.length)
      val env = scala.collection.mutable.HashMap[String, IRT.TypeVariableType]() ++ baseEnv
      var i = 0
      while (i < vars.length) {
        val tv = vars(i)
        val name = tv.getName
        val upper = erasedUpperBound(tv.getBounds, env.toMap)
        val variable = new IRT.TypeVariableType(name, upper)
        params(i) = IRT.TypeParameter(name, Some(upper))
        env += name -> variable
        i += 1
      }
      (params, env.toMap)
    }

    private def erasedUpperBound(bounds: Array[Type], env: Map[String, IRT.TypeVariableType]): IRT.ClassType = {
      if (bounds == null || bounds.isEmpty) return root
      val mapped = toOnionType(bounds(0), env)
      mapped match {
        case ap: IRT.AppliedClassType => ap.raw
        case ct: IRT.ClassType => ct
        case tv: IRT.TypeVariableType => tv.upperBound
        case _ => root
      }
    }

    def toOnionType(tp: Type, env: Map[String, IRT.TypeVariableType]): IRT.Type = tp match {
      case c: Class[?] =>
        bridge.toOnionType(c)
      case p: ParameterizedType =>
        val rawClass = p.getRawType.asInstanceOf[Class[?]]
        val raw = table.load(rawClass.getName)
        val args = p.getActualTypeArguments.map(a => toOnionType(a, env))
        if (raw == null || args.contains(null)) null
        else IRT.AppliedClassType(raw, args.toList)
      case v: TypeVariable[_] =>
        env.getOrElse(
          v.getName,
          new IRT.TypeVariableType(v.getName, erasedUpperBound(v.getBounds, env))
        )
      case a: GenericArrayType =>
        var dim = 1
        var component: Type = a.getGenericComponentType
        while (component.isInstanceOf[GenericArrayType]) {
          dim += 1
          component = component.asInstanceOf[GenericArrayType].getGenericComponentType
        }
        val componentType = toOnionType(component, env)
        if (componentType == null) null else table.loadArray(componentType, dim)
      case w: WildcardType =>
        val uppers = w.getUpperBounds
        if (uppers != null && uppers.nonEmpty) toOnionType(uppers(0), env) else root
      case _ =>
        root
    }
  }

  private class ReflectMethodRef(method: Method, override val affiliation: IRT.ClassType, mapper: GenericTypeMapper, baseEnv: Map[String, IRT.TypeVariableType]) extends IRT.Method {
    override val modifier: Int = toOnionModifier(method.getModifiers)
    override val name: String = method.getName
    private val (typeParams0, env0) = mapper.typeParamsFrom(method.getTypeParameters, baseEnv)
    override val typeParameters: Array[IRT.TypeParameter] = typeParams0.clone()
    private val argTypes: Array[IRT.Type] = method.getGenericParameterTypes.map(t => mapper.toOnionType(t, env0))
    override def arguments: Array[IRT.Type] = argTypes.clone()
    override val returnType: IRT.Type = mapper.toOnionType(method.getGenericReturnType, env0)
    val underlying: Method = method
  }

  private class ReflectFieldRef(field: Field, override val affiliation: IRT.ClassType, mapper: GenericTypeMapper, env: Map[String, IRT.TypeVariableType]) extends IRT.FieldRef {
    override val modifier: Int = toOnionModifier(field.getModifiers)
    override val name: String = field.getName
    override val `type`: IRT.Type = mapper.toOnionType(field.getGenericType, env)
    val underlying: Field = field
  }

  private class ReflectConstructorRef(constructor: Constructor[?], override val affiliation: IRT.ClassType, mapper: GenericTypeMapper, baseEnv: Map[String, IRT.TypeVariableType]) extends IRT.ConstructorRef {
    override val modifier: Int = toOnionModifier(constructor.getModifiers)
    override val name: String = "<init>"
    private val (typeParams0, env0) = mapper.typeParamsFrom(constructor.getTypeParameters, baseEnv)
    override val typeParameters: Array[IRT.TypeParameter] = typeParams0.clone()
    private val args0: Array[IRT.Type] = constructor.getGenericParameterTypes.map(t => mapper.toOnionType(t, env0))
    override def getArgs: Array[IRT.Type] = args0.clone()
    val underlying: Constructor[?] = constructor
  }

  class ReflectClassType(klass: Class[?], table: ClassTable) extends IRT.AbstractClassType {
    private val mapper = new GenericTypeMapper(table)
    private val modifier_ : Int                          = toOnionModifier(klass.getModifiers)
    private var methods_ : MultiTable[IRT.Method]        = _
    private var fields_ : OrderedTable[IRT.FieldRef]     = _
    private var constructors_ : List[IRT.ConstructorRef] = _

    private lazy val (typeParameters0, classEnv0) = mapper.typeParamsFrom(klass.getTypeParameters, Map.empty)
    override def typeParameters: Array[IRT.TypeParameter] = typeParameters0.clone()

    def isInterface: Boolean = (klass.getModifiers & java.lang.reflect.Modifier.INTERFACE) != 0

    def modifier: Int = modifier_

    def name: String = klass.getName

    def superClass: IRT.ClassType = {
      val generic = klass.getGenericSuperclass
      if (generic == null) return table.rootClass
      mapper.toOnionType(generic, classEnv0) match {
        case ct: IRT.ClassType if !(ct eq this) => ct
        case _ => table.rootClass
      }
    }

    def interfaces: Seq[IRT.ClassType] = {
      klass.getGenericInterfaces.toIndexedSeq.flatMap {
        case tpe =>
          mapper.toOnionType(tpe, classEnv0) match {
            case ct: IRT.ClassType => Some(ct)
            case _ => None
          }
      }
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
          val owner = table.load(method.getDeclaringClass.getName)
          val ownerEnv = mapper.typeParamEnv(owner.typeParameters)
          methods_.add(new ReflectMethodRef(method, owner, mapper, ownerEnv))
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
          val owner = table.load(field.getDeclaringClass.getName)
          val ownerEnv = mapper.typeParamEnv(owner.typeParameters)
          fields_.add(new ReflectFieldRef(field, owner, mapper, ownerEnv))
        }
      }
    }

    def constructors: Array[IRT.ConstructorRef] = {
      if (constructors_ == null) {
        constructors_ = new ArrayList[IRT.ConstructorRef]
        for (ctor <- klass.getConstructors) {
          constructors_.add(new ReflectConstructorRef(ctor, this, mapper, classEnv0))
        }
      }
      constructors_.toArray(new Array[IRT.ConstructorRef](0))
    }
  }
}
