package onion.compiler.typing

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{ClassType, Type}

import scala.collection.mutable.{HashMap, Set => MutableSet}

private[compiler] object AppliedTypeViews {
  /**
   * An Onion `enum Color` is declared with the raw `java.lang.Enum` as its superclass, but it
   * is an `Enum[Color]`, as javac reads a Java enum. Reading the views through that applied
   * superclass gives `Comparable[Color]`, not `Comparable[E]`, so overload selection and
   * type-argument inference see the same binding for `[T](a: Comparable[T])` (#2845).
   */
  private def enumSuperView(raw: ClassType): Option[TypedAST.AppliedClassType] =
    val sup = raw.superClass
    if (raw.modifier & onion.compiler.Modifier.ENUM) != 0 && sup != null &&
        !sup.isInstanceOf[TypedAST.AppliedClassType] && sup.name == "java.lang.Enum" &&
        sup.typeParameters.length == 1
    then Some(TypedAST.AppliedClassType(sup, List(raw)))
    else None

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
            ap.typeArguments.map(arg => TypeSubstitution.substituteType(arg, subst, scala.collection.immutable.Map.empty, defaultToBound = false))(using onion.compiler.TypedAST.typeTag)
          val specialized = TypedAST.AppliedClassType(ap.raw, specializedArgs.toList)
          val k = keyOf(specialized)
          if visited.contains(k) then return
          visited += k
          views += specialized.raw -> specialized
          val nextSubst: scala.collection.immutable.Map[String, Type] =
            specialized.raw.typeParameters.map(_.name)(using onion.compiler.TypedAST.stringTag).zip(specialized.typeArguments).toMap
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
        traverse(enumSuperView(raw).getOrElse(raw.superClass), scala.collection.immutable.Map.empty)
        raw.interfaces.foreach(traverse(_, scala.collection.immutable.Map.empty))
    views.toMap
}

