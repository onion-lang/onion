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
          // Also add non-generic parent classes to views
          val k = raw.name
          if visited.contains(k) then return
          visited += k
          val asApplied = TypedAST.AppliedClassType(raw, List())
          views += raw -> asApplied
          traverse(raw.superClass, subst)
          raw.interfaces.foreach(traverse(_, subst))

    // For MethodResolution to work correctly, we need to include the target itself
    // if it's an AppliedClassType (to provide type substitution information).
    // For checkOverrideContracts/checkAbstractMethodImplementation, we only need parents.
    // The compromise is to traverse normally but include all applied types.
    target match
      case ap: TypedAST.AppliedClassType =>
        // Include target itself for method resolution type substitution
        traverse(ap, scala.collection.immutable.Map.empty)
      case raw =>
        // For raw types, add them to views and traverse parents
        val k = raw.name
        if !visited.contains(k) then
          visited += k
          val asApplied = TypedAST.AppliedClassType(raw, List())
          views += raw -> asApplied
        traverse(raw.superClass, scala.collection.immutable.Map.empty)
        raw.interfaces.foreach(traverse(_, scala.collection.immutable.Map.empty))
    views.toMap
}

