/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import onion.compiler.{TypedAST, Modifier, OnionTypeConversion, MultiTable, OrderedTable, ClassTable}
import java.lang.reflect.{Constructor, Field, Method}
import java.lang.reflect.{GenericArrayType, ParameterizedType, Type, TypeVariable, WildcardType}
import java.util.{ArrayList, List}

object ReflectionRefs {
  import java.lang.reflect.{Modifier => JMod}
  private val reflectModifierMappings = Seq(
    JMod.PRIVATE -> Modifier.PRIVATE, JMod.PROTECTED -> Modifier.PROTECTED,
    JMod.PUBLIC -> Modifier.PUBLIC, JMod.STATIC -> Modifier.STATIC,
    JMod.SYNCHRONIZED -> Modifier.SYNCHRONIZED, JMod.ABSTRACT -> Modifier.ABSTRACT,
    JMod.FINAL -> Modifier.FINAL
  )
  private def toOnionModifier(src: Int): Int =
    reflectModifierMappings.foldLeft(0) { case (acc, (srcFlag, dstFlag)) =>
      if ((src & srcFlag) != 0) acc | dstFlag else acc
    }

  final val CONSTRUCTOR_NAME = "<init>"

  private final class GenericTypeMapper(table: ClassTable) {
    private val bridge = new OnionTypeConversion(table)
    private val root = table.rootClass

    def typeParamEnv(typeParams: Array[TypedAST.TypeParameter]): Map[String, TypedAST.TypeVariableType] = {
      val env = scala.collection.mutable.HashMap[String, TypedAST.TypeVariableType]()
      var i = 0
      while (i < typeParams.length) {
        val tp = typeParams(i)
        val upper = tp.upperBound match {
          case Some(ap: TypedAST.AppliedClassType) => ap.raw
          case Some(ct: TypedAST.ClassType) => ct
          case _ => root
        }
        env += tp.name -> new TypedAST.TypeVariableType(tp.name, upper)
        i += 1
      }
      env.toMap
    }

    def typeParamsFrom(vars: Array[? <: TypeVariable[?]], baseEnv: Map[String, TypedAST.TypeVariableType]): (Array[TypedAST.TypeParameter], Map[String, TypedAST.TypeVariableType]) = {
      val params = new Array[TypedAST.TypeParameter](vars.length)
      val env = scala.collection.mutable.HashMap[String, TypedAST.TypeVariableType]() ++ baseEnv
      var i = 0
      while (i < vars.length) {
        val tv = vars(i)
        val name = tv.getName
        val upper = erasedUpperBound(tv.getBounds, env.toMap)
        val variable = new TypedAST.TypeVariableType(name, upper)
        params(i) = TypedAST.TypeParameter(name, Some(upper))
        env += name -> variable
        i += 1
      }
      (params, env.toMap)
    }

    private def erasedUpperBound(bounds: Array[Type], env: Map[String, TypedAST.TypeVariableType]): TypedAST.ClassType = {
      if (bounds == null || bounds.isEmpty) return root
      val mapped = toOnionType(bounds(0), env)
      mapped match {
        case ap: TypedAST.AppliedClassType => ap.raw
        case tv: TypedAST.TypeVariableType => tv.upperBound
        case ct: TypedAST.ClassType => ct
        case _ => root
      }
    }

    /** Option-returning version of toOnionType for safer null handling */
    def toOnionTypeOpt(tp: Type, env: Map[String, TypedAST.TypeVariableType]): Option[TypedAST.Type] =
      Option(toOnionType(tp, env))

    def toOnionType(tp: Type, env: Map[String, TypedAST.TypeVariableType]): TypedAST.Type = tp match {
      case c: Class[?] =>
        bridge.toOnionType(c)
      case p: ParameterizedType =>
        val rawClass = p.getRawType.asInstanceOf[Class[?]]
        val raw = table.load(rawClass.getName)
        val args = p.getActualTypeArguments.map(a => toOnionType(a, env))
        if (raw == null || args.contains(null)) null
        else TypedAST.AppliedClassType(raw, args.toList)
      case v: TypeVariable[_] =>
        env.getOrElse(
          v.getName,
          new TypedAST.TypeVariableType(v.getName, erasedUpperBound(v.getBounds, env))
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
        val lowers = w.getLowerBounds
        val upper =
          if (uppers != null && uppers.nonEmpty) {
            val t = toOnionType(uppers(0), env)
            if (t == null) root else t
          } else {
            root
          }
        val lower =
          if (lowers != null && lowers.nonEmpty) {
            val t = toOnionType(lowers(0), env)
            if (t == null) None else Some(t)
          } else {
            None
          }
        new TypedAST.WildcardType(upper, lower)
      case _ =>
        root
    }
  }

  private class ReflectMethodRef(method: Method, override val affiliation: TypedAST.ClassType, mapper: GenericTypeMapper, baseEnv: Map[String, TypedAST.TypeVariableType]) extends TypedAST.Method {
    override val modifier: Int = toOnionModifier(method.getModifiers)
    override val name: String = method.getName
    private val (typeParams0, env0) = mapper.typeParamsFrom(method.getTypeParameters, baseEnv)
    override val typeParameters: Array[TypedAST.TypeParameter] = typeParams0.clone()
    private val argTypes: Array[TypedAST.Type] = method.getGenericParameterTypes.map(t => mapper.toOnionType(t, env0))
    override def arguments: Array[TypedAST.Type] = argTypes.clone()
    override val returnType: TypedAST.Type = mapper.toOnionType(method.getGenericReturnType, env0)
    val underlying: Method = method
  }

  private class ReflectFieldRef(field: Field, override val affiliation: TypedAST.ClassType, mapper: GenericTypeMapper, env: Map[String, TypedAST.TypeVariableType]) extends TypedAST.FieldRef {
    override val modifier: Int = toOnionModifier(field.getModifiers)
    override val name: String = field.getName
    override val `type`: TypedAST.Type = mapper.toOnionType(field.getGenericType, env)
    val underlying: Field = field
  }

  private class ReflectConstructorRef(constructor: Constructor[?], override val affiliation: TypedAST.ClassType, mapper: GenericTypeMapper, baseEnv: Map[String, TypedAST.TypeVariableType]) extends TypedAST.ConstructorRef {
    override val modifier: Int = toOnionModifier(constructor.getModifiers)
    override val name: String = "<init>"
    private val (typeParams0, env0) = mapper.typeParamsFrom(constructor.getTypeParameters, baseEnv)
    override val typeParameters: Array[TypedAST.TypeParameter] = typeParams0.clone()
    private val args0: Array[TypedAST.Type] = constructor.getGenericParameterTypes.map(t => mapper.toOnionType(t, env0))
    override def getArgs: Array[TypedAST.Type] = args0.clone()
    val underlying: Constructor[?] = constructor
  }

  class ReflectClassType(klass: Class[?], table: ClassTable) extends TypedAST.AbstractClassType {
    private val mapper = new GenericTypeMapper(table)
    private val modifier_ : Int                          = toOnionModifier(klass.getModifiers)
    private var methods_ : MultiTable[TypedAST.Method]        = _
    private var fields_ : OrderedTable[TypedAST.FieldRef]     = _
    private var constructors_ : List[TypedAST.ConstructorRef] = _

    private lazy val (typeParameters0, classEnv0) = mapper.typeParamsFrom(klass.getTypeParameters, Map.empty)
    override def typeParameters: Array[TypedAST.TypeParameter] = typeParameters0.clone()

    def isInterface: Boolean = (klass.getModifiers & java.lang.reflect.Modifier.INTERFACE) != 0

    def modifier: Int = modifier_

    def name: String = klass.getName

    def superClass: TypedAST.ClassType = {
      val generic = klass.getGenericSuperclass
      if (generic == null) return table.rootClass
      mapper.toOnionType(generic, classEnv0) match {
        case ct: TypedAST.ClassType if !(ct eq this) => ct
        case _ => table.rootClass
      }
    }

    def interfaces: Seq[TypedAST.ClassType] = {
      klass.getGenericInterfaces.toIndexedSeq.flatMap {
        case tpe =>
          mapper.toOnionType(tpe, classEnv0) match {
            case ct: TypedAST.ClassType => Some(ct)
            case _ => None
          }
      }
    }

    def methods: Seq[TypedAST.Method] = {
      requireMethodTable()
      methods_.values
    }

    def methods(name: String): Array[TypedAST.Method] = {
      requireMethodTable()
      methods_.get(name).toArray
    }

    private def requireMethodTable(): Unit = {
      if (methods_ == null) {
        methods_ = new MultiTable[TypedAST.Method]
        for (method <- klass.getMethods if method.getName != CONSTRUCTOR_NAME) {
          val owner = table.load(method.getDeclaringClass.getName)
          val ownerEnv = mapper.typeParamEnv(owner.typeParameters)
          methods_.add(new ReflectMethodRef(method, owner, mapper, ownerEnv))
        }
      }
    }

    def fields: Array[TypedAST.FieldRef] = {
      requireFieldTable()
      fields_.values.toArray
    }

    def field(name: String): TypedAST.FieldRef = {
      requireFieldTable()
      fields_.get(name).orNull
    }

    private def requireFieldTable(): Unit = {
      if (fields_ == null) {
        fields_ = new OrderedTable[TypedAST.FieldRef]
        for (field <- klass.getFields) {
          val owner = table.load(field.getDeclaringClass.getName)
          val ownerEnv = mapper.typeParamEnv(owner.typeParameters)
          fields_.add(new ReflectFieldRef(field, owner, mapper, ownerEnv))
        }
      }
    }

    def constructors: Array[TypedAST.ConstructorRef] = {
      if (constructors_ == null) {
        constructors_ = new ArrayList[TypedAST.ConstructorRef]
        for (ctor <- klass.getConstructors) {
          constructors_.add(new ReflectConstructorRef(ctor, this, mapper, classEnv0))
        }
      }
      constructors_.toArray(new Array[TypedAST.ConstructorRef](0))
    }
  }
}
