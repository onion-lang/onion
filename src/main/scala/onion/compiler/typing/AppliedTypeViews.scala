package onion.compiler.typing

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{ClassType, Type}

import scala.collection.mutable.{HashMap, Set => MutableSet}

private[compiler] object AppliedTypeViews {
  def collectAppliedViewsFrom(target: ClassType): scala.collection.immutable.Map[ClassType, TypedAST.AppliedClassType] =
    val views = HashMap[ClassType, TypedAST.AppliedClassType]()
    val visited = MutableSet[String]()

    def keyOf(ap: TypedAST.AppliedClassType): String =
      ap.raw.name + ap.typeArguments.map(_.name).mkString("[", ",", "]")

    def traverse(tp: ClassType, subst: scala.collection.immutable.Map[String, Type]): Unit =
      if tp == null then return
      tp match
        case ap: TypedAST.AppliedClassType =>
          val specializedArgs =
            ap.typeArguments.map(arg => TypeSubstitution.substituteType(arg, subst, scala.collection.immutable.Map.empty, defaultToBound = false))
          val specialized = TypedAST.AppliedClassType(ap.raw, specializedArgs.toList)
          val k = keyOf(specialized)
          if visited.contains(k) then return
          visited += k
          views += specialized.raw -> specialized
          val nextSubst: scala.collection.immutable.Map[String, Type] =
            specialized.raw.typeParameters.map(_.name).zip(specialized.typeArguments).toMap
          traverse(specialized.raw.superClass, nextSubst)
          specialized.raw.interfaces.foreach(traverse(_, nextSubst))
        case raw =>
          traverse(raw.superClass, subst)
          raw.interfaces.foreach(traverse(_, subst))

    traverse(target, scala.collection.immutable.Map.empty)
    views.toMap
}

