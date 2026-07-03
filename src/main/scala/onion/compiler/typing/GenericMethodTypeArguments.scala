package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import scala.collection.mutable.HashMap

private[typing] object GenericMethodTypeArguments {
  /**
   * Merge two inferred bindings that differ only in nullability: a parameter
   * unified with both String and String? infers String?. Returns null when
   * the bindings are genuinely incompatible.
   */
  private def mergeNullability(a: Type, b: Type): Type = (a, b) match {
    case (an: NullableType, bn) if an.innerType eq bn => an
    case (an, bn: NullableType) if bn.innerType eq an => bn
    case _ => null
  }

  def explicitFromMappedArgs(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    mappedArgs: Array[Type],
    classSubst: scala.collection.immutable.Map[String, Type],
    reportErrors: Boolean = true,
    typeArgNodes: Array[AST.TypeNode] = null
  ): Option[scala.collection.immutable.Map[String, Type]] = {
    val rootClass = typing.rootClass
    def reportError(node: AST.Node, error: SemanticError, items: AnyRef*): Unit =
      typing.report(error, node, items*)
    def boxedTypeArg(arg: Type): Type = typing.boxedTypeArgument(arg)
    val typeParams = method.typeParameters
    if (typeParams.isEmpty) {
      if (reportErrors) {
        reportError(callNode, METHOD_NOT_GENERIC, method.affiliation.name, method.name)
      }
      return None
    }
    if (typeParams.length != mappedArgs.length) {
      if (reportErrors) {
        reportError(
          callNode,
          METHOD_TYPE_ARGUMENT_ARITY_MISMATCH,
          method.affiliation.name,
          method.name,
          Integer.valueOf(typeParams.length),
          Integer.valueOf(mappedArgs.length)
        )
      }
      return None
    }

    val subst = typeParams.zip(mappedArgs).map { case (p, a) => p.name -> a }.toMap

    val allBoundsOk = typeParams.indices.forall { i =>
      val tp = typeParams(i)
      val upper0 = tp.upperBound.getOrElse(rootClass)
      val upper = TypeSubstitution.substituteType(upper0, classSubst, subst, defaultToBound = true)
      val arg = mappedArgs(i)
      // Nullability must be checked apart from assignability: Object accepts
      // T? by the top-type rule, so isAssignable can't reject String? for a
      // non-null Object bound on its own
      val ok = arg match {
        case n: NullableType =>
          tp.nullability != Nullability.NonNull && TypeRules.isAssignable(upper, boxedTypeArg(n.innerType))
        case _ =>
          TypeRules.isAssignable(upper, boxedTypeArg(arg))
      }
      if (!ok) {
        if (reportErrors) {
          val position = if (typeArgNodes == null || i >= typeArgNodes.length) callNode else typeArgNodes(i)
          reportError(position, INCOMPATIBLE_TYPE, upper, arg)
        }
        false
      } else true
    }
    if (!allBoundsOk) return None

    Some(subst)
  }

  def infer(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type]
  ): scala.collection.immutable.Map[String, Type] =
    infer(typing, callNode, method, args, classSubst, null)

  /**
   * Infer type arguments, but only return those that were actually constrained.
   * Type parameters that would default to their bound are NOT included in the result.
   * This is useful for preliminary inference before closure typing, where we want
   * to preserve type variables for closure return type inference.
   *
   * `args` is aligned positionally with the method's formal parameters and MAY
   * contain `null` slots for arguments not yet typed (e.g. untyped-parameter
   * closures whose SAM parameter types are still being inferred). Those slots
   * are skipped, so the remaining arguments still unify against their correct
   * formal positions regardless of argument order (issue #256). Callers that
   * hold only the resolved arguments must preserve their positions rather than
   * collapsing them, or a determining argument after a closure would unify
   * against the wrong formal and leave a type variable unbound.
   */
  def inferWithoutDefaults(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    expectedReturn: Type = null
  ): scala.collection.immutable.Map[String, Type] = {
    val rootClass = typing.rootClass
    def reportError(node: AST.Node, error: SemanticError, items: AnyRef*): Unit =
      typing.report(error, node, items*)
    def boxedTypeArg(arg: Type): Type = typing.boxedTypeArgument(arg)
    val typeParams = method.typeParameters
    if (typeParams.isEmpty) return scala.collection.immutable.Map.empty

    def isSuperTypeForBounds(left: Type, right: Type): Boolean =
      if (!left.isBasicType && right.isBasicType) TypeRules.isSuperType(left, boxedTypeArg(right))
      else TypeRules.isSuperType(left, right)

    val inferred = HashMap[String, Type]()
    val upperConstraints = HashMap[String, Type]()
    val lowerConstraints = HashMap[String, Type]()
    val paramNames = typeParams.map(_.name).toSet

    def addUpper(name: String, bound: Type, position: AST.Node): Unit = {
      if (bound == null || bound.isNullType) return
      upperConstraints.get(name) match
        case None =>
          upperConstraints += name -> bound
        case Some(prev) =>
          if (isSuperTypeForBounds(prev, bound)) upperConstraints += name -> bound
          else if (isSuperTypeForBounds(bound, prev)) ()
          else reportError(position, INCOMPATIBLE_TYPE, prev, bound)
    }

    def addLower(name: String, bound: Type): Unit = {
      if (bound == null || bound.isNullType) return
      lowerConstraints.get(name) match
        case None =>
          lowerConstraints += name -> bound
        case Some(prev) =>
          if (isSuperTypeForBounds(prev, bound)) ()
          else if (isSuperTypeForBounds(bound, prev)) lowerConstraints += name -> bound
          else lowerConstraints += name -> rootClass
    }

    def unify(formal: Type, actual: Type, position: AST.Node): Unit = {
      if (actual.isNullType) return
      formal match
        case w: TypedAST.WildcardType =>
          w.lowerBound match
            case Some(lb) =>
              lb match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addUpper(tv.name, actual, position)
                case _ =>
                  unify(lb, actual, position)
            case None =>
              w.upperBound match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addLower(tv.name, actual)
                case _ =>
                  unify(w.upperBound, actual, position)
        case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
          // Type arguments are reference types: box primitives (Future::async
          // of an Int-returning lambda is a Future[Integer], not Future[int])
          val bound = actual match {
            case bt: BasicType if bt != BasicType.VOID => typing.boxedTypeArgument(bt)
            // A smart-cast non-null view of a variable still binds the
            // declared variable, so occurrences across arguments stay eq
            case tva: TypedAST.TypeVariableType => tva.widen
            case other => other
          }
          inferred.get(tv.name) match {
            case Some(prev) =>
              if (!(prev eq bound)) {
                val merged = mergeNullability(prev, bound)
                if (merged != null) inferred += tv.name -> merged
                else reportError(position, INCOMPATIBLE_TYPE, prev, bound)
              }
            case None =>
              inferred += tv.name -> bound
          }
        case fn: TypedAST.NullableType =>
          // A nullable formal such as `T?` (e.g. a `List[T?]` parameter) must
          // still bind its type variable: unwrap the formal's nullability and
          // unify the inner type against the actual's non-null view. Without
          // this, `T?` matched no case and T was left unbound (defaulting to
          // Object), so a generic call over `List[T?]` never inferred T.
          val innerActual = actual match {
            case an: TypedAST.NullableType => an.innerType
            case _ => actual
          }
          unify(fn.innerType, innerActual, position)
        case af: TypedAST.ArrayType =>
          actual match {
            case aa: TypedAST.ArrayType => unify(af.base, aa.base, position)
            case _ =>
          }
        case apf: TypedAST.AppliedClassType =>
          def sameRawClass(c1: TypedAST.ClassType, c2: TypedAST.ClassType): Boolean =
            (c1 eq c2) || (c1.name == c2.name)

          def unifyWithApplied(apa: TypedAST.AppliedClassType): Unit =
            if sameRawClass(apf.raw, apa.raw) && apf.typeArguments.length == apa.typeArguments.length then
              apf.typeArguments.zip(apa.typeArguments).foreach { (f, a) => unify(f, a, position) }

          actual match
            case apa: TypedAST.AppliedClassType =>
              if sameRawClass(apf.raw, apa.raw) then unifyWithApplied(apa)
              else
                val views = AppliedTypeViews.collectAppliedViewsFrom(apa)
                views.get(apf.raw).orElse(views.find((k, _) => k.name == apf.raw.name).map(_._2)) match
                  case Some(view) => unifyWithApplied(view)
                  case None =>
            case ct: ClassType =>
              val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
              views.get(apf.raw).orElse(views.find((k, _) => k.name == apf.raw.name).map(_._2)) match
                case Some(view) => unifyWithApplied(view)
                case None =>
            case _ =>
        case aft: ArrayType =>
          actual match {
            case aat: ArrayType if aft.dimension == aat.dimension =>
              unify(aft.component, aat.component, position)
            case _ =>
          }
        case _ =>
    }

    val formalArgs =
      method.arguments.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    if (method.isVararg && formalArgs.nonEmpty) {
      // Vararg methods: unify fixed params positionally, then unify the vararg
      // component with each trailing argument (boxing primitives), unless the
      // caller already passes a packed array. Null slots (untyped closures not
      // yet resolved) are skipped so positions stay aligned (issue #256).
      val fixedCount = formalArgs.length - 1
      formalArgs.take(fixedCount).zip(args.take(fixedCount)).foreach { (formal, actual) => if (actual != null) unify(formal, actual.`type`, callNode) }
      val packed = args.length == formalArgs.length && (args.last != null) && args.last.`type`.isArrayType
      if (packed) {
        unify(formalArgs.last, args.last.`type`, callNode)
      } else {
        val component = formalArgs.last match {
          case at: ArrayType => at.base
          case other => other
        }
        args.drop(fixedCount).foreach { actual =>
          if (actual != null) {
            val actualType = if (actual.`type`.isBasicType) typing.boxedTypeArgument(actual.`type`.asInstanceOf[BasicType]) else actual.`type`
            unify(component, actualType, callNode)
          }
        }
      }
    } else {
      // Skip null actuals (untyped closures whose SAM parameter types are still
      // being inferred): remaining arguments stay aligned to their formals so a
      // determining argument after a closure is still unified (issue #256).
      formalArgs.zip(args).foreach { (formal, actual) => if (actual != null) unify(formal, actual.`type`, callNode) }
    }

    if (expectedReturn != null) {
      val formalReturn =
        TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
      unify(formalReturn, expectedReturn, callNode)
    }

    // Only include type parameters that were actually constrained
    // (either directly inferred or have lower constraints)
    val result = HashMap[String, Type]()
    for (tp <- typeParams) {
      val name = tp.name
      inferred.get(name).orElse(lowerConstraints.get(name)).foreach { t =>
        result += name -> t
      }
    }

    result.toMap
  }

  def explicit(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type]
  ): Option[scala.collection.immutable.Map[String, Type]] = {
    val mappedArgsOpt = typeArgs.foldLeft(Option(Array.empty[Type])) { (accOpt, typeArg) =>
      accOpt.flatMap { acc =>
        typing.mapFrom(typeArg).flatMap { mapped =>
          if (mapped eq BasicType.VOID) {
            typing.report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeArg, mapped.name)
            None
          } else Some(acc :+ mapped)
        }
      }
    }
    mappedArgsOpt.flatMap(mappedArgs =>
      explicitFromMappedArgs(typing, callNode, method, mappedArgs, classSubst, typeArgNodes = typeArgs.toArray)
    )
  }

  def infer(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    expectedReturn: Type
  ): scala.collection.immutable.Map[String, Type] = {
    val rootClass = typing.rootClass
    def reportError(node: AST.Node, error: SemanticError, items: AnyRef*): Unit =
      typing.report(error, node, items*)
    def boxedTypeArg(arg: Type): Type = typing.boxedTypeArgument(arg)
    val typeParams = method.typeParameters
    if (typeParams.isEmpty) return scala.collection.immutable.Map.empty

    def isSuperTypeForBounds(left: Type, right: Type): Boolean =
      if (!left.isBasicType && right.isBasicType) TypeRules.isSuperType(left, boxedTypeArg(right))
      else TypeRules.isSuperType(left, right)

    def isAssignableForBounds(left: Type, right: Type): Boolean =
      if (!left.isBasicType && right.isBasicType) TypeRules.isAssignable(left, boxedTypeArg(right))
      else TypeRules.isAssignable(left, right)

    val bounds = HashMap[String, Type]()
    for (tp <- typeParams) {
      val upper = tp.upperBound.getOrElse(rootClass)
      bounds += tp.name -> TypeSubstitution.substituteType(upper, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
    }

    val inferred = HashMap[String, Type]()
    val upperConstraints = HashMap[String, Type]()
    val lowerConstraints = HashMap[String, Type]()
    val paramNames = typeParams.map(_.name).toSet

    def addUpper(name: String, bound: Type, position: AST.Node): Unit = {
      if (bound == null || bound.isNullType) return
      upperConstraints.get(name) match
        case None =>
          upperConstraints += name -> bound
        case Some(prev) =>
          if (isSuperTypeForBounds(prev, bound)) upperConstraints += name -> bound
          else if (isSuperTypeForBounds(bound, prev)) ()
          else reportError(position, INCOMPATIBLE_TYPE, prev, bound)
    }

    def addLower(name: String, bound: Type): Unit = {
      if (bound == null || bound.isNullType) return
      lowerConstraints.get(name) match
        case None =>
          lowerConstraints += name -> bound
        case Some(prev) =>
          if (isSuperTypeForBounds(prev, bound)) ()
          else if (isSuperTypeForBounds(bound, prev)) lowerConstraints += name -> bound
          else lowerConstraints += name -> rootClass
    }

    def unify(formal: Type, actual: Type, position: AST.Node): Unit = {
      if (actual.isNullType) return
      formal match
        case w: TypedAST.WildcardType =>
          w.lowerBound match
            case Some(lb) =>
              lb match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addUpper(tv.name, actual, position)
                case _ =>
                  unify(lb, actual, position)
            case None =>
              w.upperBound match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addLower(tv.name, actual)
                case _ =>
                  unify(w.upperBound, actual, position)
        case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
          // Type arguments are reference types: box primitives (Future::async
          // of an Int-returning lambda is a Future[Integer], not Future[int])
          val bound = actual match {
            case bt: BasicType if bt != BasicType.VOID => typing.boxedTypeArgument(bt)
            // A smart-cast non-null view of a variable still binds the
            // declared variable, so occurrences across arguments stay eq
            case tva: TypedAST.TypeVariableType => tva.widen
            case other => other
          }
          inferred.get(tv.name) match {
            case Some(prev) =>
              if (!(prev eq bound)) {
                val merged = mergeNullability(prev, bound)
                if (merged != null) inferred += tv.name -> merged
                else reportError(position, INCOMPATIBLE_TYPE, prev, bound)
              }
            case None =>
              inferred += tv.name -> bound
          }
        case fn: TypedAST.NullableType =>
          // A nullable formal such as `T?` (e.g. a `List[T?]` parameter) must
          // still bind its type variable: unwrap the formal's nullability and
          // unify the inner type against the actual's non-null view. Without
          // this, `T?` matched no case and T was left unbound (defaulting to
          // Object), so a generic call over `List[T?]` never inferred T.
          val innerActual = actual match {
            case an: TypedAST.NullableType => an.innerType
            case _ => actual
          }
          unify(fn.innerType, innerActual, position)
        case af: TypedAST.ArrayType =>
          actual match {
            case aa: TypedAST.ArrayType => unify(af.base, aa.base, position)
            case _ =>
          }
        case apf: TypedAST.AppliedClassType =>
          // 同じクラス名で型引数の数が一致するかチェック（参照比較ではなく名前比較）
          def sameRawClass(c1: TypedAST.ClassType, c2: TypedAST.ClassType): Boolean =
            (c1 eq c2) || (c1.name == c2.name)

          def unifyWithApplied(apa: TypedAST.AppliedClassType): Unit =
            if sameRawClass(apf.raw, apa.raw) && apf.typeArguments.length == apa.typeArguments.length then
              apf.typeArguments.zip(apa.typeArguments).foreach { (f, a) => unify(f, a, position) }

          actual match
            case apa: TypedAST.AppliedClassType =>
              if sameRawClass(apf.raw, apa.raw) then unifyWithApplied(apa)
              else
                val views = AppliedTypeViews.collectAppliedViewsFrom(apa)
                views.get(apf.raw).orElse(views.find((k, _) => k.name == apf.raw.name).map(_._2)) match
                  case Some(view) => unifyWithApplied(view)
                  case None =>
            case ct: ClassType =>
              val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
              views.get(apf.raw).orElse(views.find((k, _) => k.name == apf.raw.name).map(_._2)) match
                case Some(view) => unifyWithApplied(view)
                case None =>
            case _ =>
        case aft: ArrayType =>
          actual match {
            case aat: ArrayType if aft.dimension == aat.dimension =>
              unify(aft.component, aat.component, position)
            case _ =>
          }
        case _ =>
    }

    val formalArgs =
      method.arguments.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    if (method.isVararg && formalArgs.nonEmpty) {
      // Vararg methods: unify fixed params positionally, then unify the vararg
      // component with each trailing argument (boxing primitives), unless the
      // caller already passes a packed array.
      val fixedCount = formalArgs.length - 1
      formalArgs.take(fixedCount).zip(args.take(fixedCount)).foreach { (formal, actual) => unify(formal, actual.`type`, callNode) }
      val packed = args.length == formalArgs.length && args.last.`type`.isArrayType
      if (packed) {
        unify(formalArgs.last, args.last.`type`, callNode)
      } else {
        val component = formalArgs.last match {
          case at: ArrayType => at.base
          case other => other
        }
        args.drop(fixedCount).foreach { actual =>
          val actualType = if (actual.`type`.isBasicType) typing.boxedTypeArgument(actual.`type`.asInstanceOf[BasicType]) else actual.`type`
          unify(component, actualType, callNode)
        }
      }
    } else {
      formalArgs.zip(args).foreach { (formal, actual) => unify(formal, actual.`type`, callNode) }
    }

    if (expectedReturn != null) {
      val formalReturn =
        TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
      unify(formalReturn, expectedReturn, callNode)
    }

    for (tp <- typeParams) {
      val name = tp.name
      val bound0 = bounds(name)
      val bound =
        upperConstraints.get(name) match
          case None => bound0
          case Some(upper) =>
            if (isSuperTypeForBounds(bound0, upper)) upper
            else if (isSuperTypeForBounds(upper, bound0)) bound0
            else {
              reportError(callNode, INCOMPATIBLE_TYPE, bound0, upper)
              bound0
            }

      val inferredType0 =
        inferred.get(name)
          .orElse(lowerConstraints.get(name))
          .getOrElse(bound)

      // Nullable inferences satisfy the bound through their inner type and
      // are rejected outright for non-null parameters (the Object top-type
      // rule would otherwise let String? through an Object bound)
      val boundsOk = inferredType0 match {
        case n: NullableType =>
          tp.nullability != Nullability.NonNull && isAssignableForBounds(bound, n.innerType)
        case other =>
          isAssignableForBounds(bound, other)
      }
      val inferredType =
        if (!boundsOk) {
          reportError(callNode, INCOMPATIBLE_TYPE, bound, inferredType0)
          bound
        } else {
          inferredType0
        }

      inferred += name -> inferredType
    }

    inferred.toMap
  }
}
