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

  private def mentionsTypeParam(t: Type, names: Set[String]): Boolean = t match {
    case tv: TypeVariableType => names.contains(tv.name)
    case applied: AppliedClassType => applied.typeArguments.exists(mentionsTypeParam(_, names))
    case at: ArrayType => mentionsTypeParam(at.base, names)
    case n: NullableType => mentionsTypeParam(n.innerType, names)
    case w: WildcardType => mentionsTypeParam(w.upperBound, names) || w.lowerBound.exists(mentionsTypeParam(_, names))
    case _ => false
  }

  /** Whether `t` mentions any type variable, a method's or an enclosing class's. */
  private def mentionsTypeVariable(t: Type): Boolean = t match {
    case _: TypeVariableType => true
    case applied: AppliedClassType => applied.typeArguments.exists(mentionsTypeVariable)
    case at: ArrayType => mentionsTypeVariable(at.base)
    case n: NullableType => mentionsTypeVariable(n.innerType)
    case w: WildcardType => mentionsTypeVariable(w.upperBound) || w.lowerBound.exists(mentionsTypeVariable)
    case _ => false
  }

  private def typeParamsIn(t: Type, names: Set[String]): Set[String] = t match {
    case tv: TypeVariableType => if (names.contains(tv.name)) Set(tv.name) else Set.empty
    case applied: AppliedClassType => applied.typeArguments.toSet.flatMap(typeParamsIn(_, names))
    case at: ArrayType => typeParamsIn(at.base, names)
    case n: NullableType => typeParamsIn(n.innerType, names)
    case w: WildcardType => typeParamsIn(w.upperBound, names) ++ w.lowerBound.toSet.flatMap(typeParamsIn(_, names))
    case _ => Set.empty
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

    // Box primitive type arguments (Int -> Integer) so that the substitution
    // map matches what inference produces via Constraints.unify, which boxes
    // primitives before inserting them.  Without boxing, List[T] with T=int
    // becomes List[int], and the invariant type-argument check rejects
    // ArrayList[Integer] even though the code is correct.
    val subst = typeParams.zip(mappedArgs).map { case (p, a) => p.name -> boxedTypeArg(a) }.toMap

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
  ): scala.collection.immutable.Map[String, Type] =
    if (method.typeParameters.isEmpty) scala.collection.immutable.Map.empty
    else new Constraints(typing, callNode, method, args, classSubst, expectedReturn, reportErrors = true).constrainedOnly()

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
    expectedReturn: Type,
    reportErrors: Boolean = true
  ): scala.collection.immutable.Map[String, Type] =
    if (method.typeParameters.isEmpty) scala.collection.immutable.Map.empty
    else new Constraints(typing, callNode, method, args, classSubst, expectedReturn, reportErrors).withDefaults()

  /**
   * `infer` for overload selection: `None` when this call leaves no type argument that meets
   * every limit it places on it, so the method does not apply. "A generic method applies only
   * if some `U` satisfies all its limits" (#2431): `Collections::copy(List[T], List[Object])`
   * has none, and another overload is chosen instead, or the call is E0005.
   */
  def inferApplicable(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    expectedReturn: Type
  ): Option[scala.collection.immutable.Map[String, Type]] =
    if (method.typeParameters.isEmpty) Some(scala.collection.immutable.Map.empty)
    else {
      val constraints = new Constraints(typing, callNode, method, args, classSubst, expectedReturn, reportErrors = false)
      val subst = constraints.withDefaults()
      Option.unless(constraints.limitsConflict)(subst)
    }

  /**
   * The type-argument constraints one call places on a generic method: every formal
   * parameter (and the expected return type, when known) unified against the actual, into
   * direct bindings (`inferred`), lower bounds from wildcard uppers and upper bounds from
   * wildcard lowers. `constrainedOnly()` and `withDefaults()` are the two ways of reading
   * the result; the collection itself is the same for both.
   *
   * `args` MAY contain `null` slots for arguments not yet typed (untyped-parameter closures
   * whose SAM parameter types are still being inferred). Those slots are skipped, so the
   * remaining arguments still unify against their correct formal positions (issue #256).
   *
   * `reportErrors`: during overload-applicability collection this inference runs over EVERY
   * candidate, including ones a later arity/specificity check discards. Emitting bound-check
   * errors there leaks a bounded overload's constraint onto the call even when an unbounded
   * overload is the one selected (issue #298), so the collection phase passes false; the
   * final resolution of the SELECTED method keeps errors on.
   */
  private final class Constraints(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    expectedReturn: Type,
    reportErrors: Boolean
  ) {
    val rootClass = typing.rootClass
    def reportError(node: AST.Node, error: SemanticError, items: AnyRef*): Unit =
      if (reportErrors) {
        if (finalInference) typing.reportBoundError(callNode, error, node, items*)
        else typing.report(error, node, items*)
      }
    // Set by `withDefaults()` once the constraints are collected: from there on it checks the
    // type arguments the call is typed with against their bounds, and a bound error found
    // while reporting is suppressed is kept with the call's term (#2538). A unification
    // conflict met while collecting, or a preliminary `constrainedOnly()` reading, can come
    // from a speculative expected type and keeps nothing.
    private var finalInference = false
    def boxedTypeArg(arg: Type): Type = typing.boxedTypeArgument(arg)
    val typeParams = method.typeParameters

    def isSuperTypeForBounds(left: Type, right: Type): Boolean =
      if (!left.isBasicType && right.isBasicType) TypeRules.isSuperType(left, boxedTypeArg(right))
      else TypeRules.isSuperType(left, right)

    // A limit takes a value only as the shared join rule does: a type variable (`T`, `T?`)
    // takes only its own values, and type arguments that mention one must match, at any
    // depth (`List[List[T]]` does not take an `ArrayList[Object]`, #2431).
    // A wildcard limit counts as its bound: `? super String?` (a lambda's `Comparator[? super T]`)
    // is the limit `String?`; `?` and `? extends X` limit nothing from above.
    def fitsBothLimits(upper0: Type, lower: Type): Boolean = {
      val upper = upper0 match
        case w: TypedAST.WildcardType => w.lowerBound.orNull
        case t => t
      upper == null || {
        val l = if (!upper.isBasicType && lower.isBasicType) boxedTypeArg(lower) else lower
        TypeCheckingHelpers.joinsStrictly(typing.table_, upper, l)
      }
    }

    /** Set by `withDefaults()`: some type parameter has an upper and a lower limit no type meets. */
    var limitsConflict: Boolean = false

    // A bound is read with its type arguments, not by erasure: a `Cat` that is an
    // `Animal[Dog]` does not meet `Animal[Cat]`, and an `Integer` does not meet
    // `Comparable[String]` (#2780).
    def isAssignableForBounds(left: Type, right: Type): Boolean = {
      val r = if (!left.isBasicType && right.isBasicType) boxedTypeArg(right) else right
      TypeRules.isAssignable(left, r) && !TypeRelations.ownViewRejects(typing.table_, left, r)
    }

    val bounds = HashMap[String, Type]()
    for (tp <- typeParams) {
      val upper = tp.upperBound.getOrElse(rootClass)
      bounds += tp.name -> TypeSubstitution.substituteType(upper, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
    }

    val inferred = HashMap[String, Type]()
    // The bindings the arguments fixed, before the expected return type is unified in.
    private var inferredFromArgs: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
    val upperConstraints = HashMap[String, Type]()
    val lowerConstraints = HashMap[String, Type]()
    val paramNames = typeParams.iterator.map(_.name).toSet

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

    def unify(formal: Type, actual: Type, position: AST.Node, returnContext: Boolean = false): Unit = {
      if (actual.isNullType) return
      formal match
        case w: TypedAST.WildcardType =>
          w.lowerBound match
            case Some(lb) =>
              lb match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addUpper(tv.name, actual, position)
                case _ =>
                  unify(lb, actual, position, returnContext)
            case None =>
              w.upperBound match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addLower(tv.name, actual)
                case _ =>
                  unify(w.upperBound, actual, position, returnContext)
        case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
          // Type arguments are reference types: box primitives (Future::async
          // of an Int-returning lambda is a Future[Integer], not Future[int])
          val bound = actual match {
            case bt: BasicType if bt != BasicType.VOID => typing.boxedTypeArgument(bt)
            // A smart-cast non-null view of a variable still binds the
            // declared variable, so occurrences across arguments stay eq
            case tva: TypedAST.TypeVariableType => tva.widen
            // `Int?` as an expected return: box inside the `?`, so it merges
            // with the boxed `Integer` the arguments gave (issue #2666). Only
            // when an argument already bound T to that boxed type: an `Int?`
            // argument, or a T fixed by the expected type alone, keeps T
            // unboxed, as before, since T decides overloads later
            // (`remove(ident(w)!!)` and a lambda's `remove(x!!)` are remove(int)).
            case n: TypedAST.NullableType
                if returnContext && n.innerType.isBasicType && n.innerType != BasicType.VOID && {
                  val boxed = typing.boxedTypeArgument(n.innerType)
                  inferred.get(tv.name).exists {
                    case p: TypedAST.NullableType => p.innerType eq boxed
                    case p => p eq boxed
                  }
                } =>
              TypedAST.NullableType.of(typing.boxedTypeArgument(n.innerType))
            case other => other
          }
          inferred.get(tv.name) match {
            case Some(prev) =>
              if (!(prev eq bound)) {
                val merged = mergeNullability(prev, bound)
                if (merged != null) inferred += tv.name -> merged
                else if (returnContext && (bound eq rootClass)) {
                  // This unification comes from the call's expected RETURN type,
                  // and it collapsed to plain Object -- either a caller-side
                  // wildcard target (`Function1[Int, ?]`)'s irrelevant result
                  // position collapsing to Object, or an explicit `Object`
                  // return slot. Either way it is not a real constraint (Object
                  // accepts anything), so it must not conflict with a concrete
                  // binding this same call already inferred from its own
                  // arguments (issue #1972).
                  ()
                } else {
                  reportError(position, INCOMPATIBLE_TYPE, prev, bound)
                }
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
          unify(fn.innerType, innerActual, position, returnContext)
        case af: TypedAST.ArrayType =>
          actual match {
            case aa: TypedAST.ArrayType => unify(af.base, aa.base, position, returnContext)
            case _ =>
          }
        case apf: TypedAST.AppliedClassType =>
          // 同じクラス名で型引数の数が一致するかチェック（参照比較ではなく名前比較）
          def sameRawClass(c1: TypedAST.ClassType, c2: TypedAST.ClassType): Boolean =
            (c1 eq c2) || (c1.name == c2.name)

          def unifyWithApplied(apa: TypedAST.AppliedClassType): Unit =
            if sameRawClass(apf.raw, apa.raw) && apf.typeArguments.length == apa.typeArguments.length then
              apf.typeArguments.zip(apa.typeArguments).foreach { (f, a) => unify(f, a, position, returnContext) }

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
            // A primitive argument is passed boxed, so an applied formal reads T off the
            // box's own view: `id(5)` against `Comparable[T]` binds T = Integer, as Java
            // infers, instead of leaving T to default to Object (#2594).
            case bt: BasicType if bt != BasicType.VOID && !bt.isNullType =>
              unify(apf, typing.boxedTypeArgument(bt), position, returnContext)
            case _ =>
        case _ =>
    }


    /** Unifies every formal parameter, and the expected return type when known, against the actuals. */
    private def collect(): Unit = {
      val formalArgs =
        method.arguments.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))(using onion.compiler.TypedAST.typeTag)
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

      inferredFromArgs = inferred.toMap

      if (expectedReturn != null) {
        val formalReturn =
          TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
        // Snapshot arg-side bindings before unifying against the expected return type.
        // Left-to-right rule: if return-type unification only widens a binding from T
        // to T? (e.g. maxBy result assigned to Shape? widening T from Shape to Shape?),
        // revert to the arg-side binding; the outer assignment handles the nullable
        // promotion without invalidating the receiver type (List[Shape] vs List[Shape?]).
        val inferredBeforeReturn = inferred.toMap
        // The arguments come first: when they fix every type parameter of the return
        // type and that return already fits the expected type, the expected type adds
        // nothing. `val n: Number = id(5)` keeps T = Integer, as javac infers, instead
        // of conflicting with it (#2594). `val s: Comparable[String] = id(5)` does not
        // fit, so it still unifies and is rejected.
        // `isAssignable` reads an applied target by erasure, so `Integer` would "fit"
        // `Comparable[String]`. A raw class reaching an applied type must have that type
        // among its own views: `val c: Comparable[String] = id(5)` stays an error (#2594).
        def appliedViewFits(expected: Type, actual: Type): Boolean = {
          val target = expected match
            case n: NullableType => n.innerType
            case t => t
          (target, actual) match
            case (applied: AppliedClassType, raw: ClassType) if !raw.isInstanceOf[AppliedClassType] =>
              TypeRelations.boxSupertypeAccepts(typing.table_, applied, raw)
            case _ => true
        }
        // A parameter fixed through a wildcard (`Collection[? extends T]` given a
        // `List[String]`) has only a lower constraint, and counts as fixed too:
        // `val o: Object = Collections::max(xs, c)` keeps T = String (#2857).
        val argFixed = lowerConstraints.toMap ++ inferred.toMap
        val returnFitsArgs = {
          val names = typeParamsIn(formalReturn, paramNames)
          names.nonEmpty && names.forall(argFixed.contains) && {
            val argReturn = TypeSubstitution.substituteType(formalReturn, classSubst, argFixed, defaultToBound = false)
            !expectedReturn.isBasicType && TypeRules.isAssignable(expectedReturn, argReturn) &&
              appliedViewFits(expectedReturn, argReturn)
          }
        }
        if (!returnFitsArgs) unify(formalReturn, expectedReturn, callNode, returnContext = true)
        for ((name, before) <- inferredBeforeReturn) {
          inferred.get(name).foreach { after =>
            if ((after ne before) && (mergeNullability(before, after) eq after))
              inferred(name) = before
          }
        }
      }

    }

    private def fixedByCall(): Set[String] = {
      val names = typeParams.map(_.name).toSet
      def informative(t: Term): Boolean = t != null && !t.`type`.isNullType && (t match {
        case l: ListLiteral => l.elements.nonEmpty
        case m: MapLiteral => m.keys.nonEmpty
        case _ => true
      })
      val formals = method.arguments
      val fromArgs =
        if (formals.isEmpty) Set.empty[String]
        else args.indices.toSet.flatMap { i =>
          if (informative(args(i))) typeParamsIn(formals(math.min(i, formals.length - 1)), names) else Set.empty[String]
        }
      val fromReturn = if (expectedReturn != null) typeParamsIn(method.returnType, names) else Set.empty[String]
      fromArgs ++ fromReturn
    }

    /** Only the type parameters that were actually constrained: no defaults to bounds, so closure return inference still sees type variables. */
    def constrainedOnly(): scala.collection.immutable.Map[String, Type] = {
      collect()
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

    /** Every type parameter: inferred, else its lower constraint, else its (upper-constrained) bound; violations reported. */
    def withDefaults(): scala.collection.immutable.Map[String, Type] = {
      collect()
      finalInference = true
      // Parameters an argument or the expected type actually fixed. A `null` argument, an
      // empty `[]`/`[:]` literal, or no mention at all fixes nothing: such a parameter is its
      // bound (or `Object`) by default. Only fixed ones are re-checked against a chained bound.
      val pinned = fixedByCall().filter(n => inferred.contains(n) || lowerConstraints.contains(n))
      val failedBound = scala.collection.mutable.Set[String]()
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
                failedBound += name
                bound0
              }

        // `? super U` fixed an upper limit and `? extends U` a lower one: some U must fit both
        // (`Collections::copy(List[T], List[Object])` has none, #2431).
        for (upper <- upperConstraints.get(name); lower <- lowerConstraints.get(name)
             if !failedBound(name) && !fitsBothLimits(upper, lower)) {
          reportError(callNode, INCOMPATIBLE_TYPE, upper, lower)
          limitsConflict = true
          failedBound += name
        }

        // The same holds for a value a plain `T` argument fixed, when the `? super T` limit
        // mentions a type variable: erased, `T` would take an `Object`, which a concrete limit
        // already refuses (`Collections::fill(x: List[T], v: Object)`, #2695). Only an argument
        // passes a value in: `val o: Object = Collections::max(x: List[T], c: Comparator[T])`
        // fixes `T = Object` from the target alone, and stays accepted.
        for (upper <- upperConstraints.get(name) if !failedBound(name) && mentionsTypeVariable(upper);
             fixed <- inferredFromArgs.get(name) if !fitsBothLimits(upper, fixed)) {
          reportError(callNode, INCOMPATIBLE_TYPE, upper, fixed)
          limitsConflict = true
          failedBound += name
        }

        val fixedType = inferred.get(name).orElse(lowerConstraints.get(name))
        val inferredType0 = fixedType.getOrElse(bound)

        // An F-bound (`T extends Comparable[T]`) names the parameter itself. Read with `T`
        // erased it is `Comparable[Object]`, which no `String` or `U extends Comparable[U]`
        // meets. Check a fixed `T` against its bound with `T` read as that type, the way
        // `Comparable[String]` is the bound a `String` must meet (#2338).
        val selfBound = (fixedType, tp.upperBound) match
          case (Some(fixed), Some(upper0)) if !upperConstraints.contains(name) && mentionsTypeParam(upper0, Set(name)) =>
            val self = fixed match
              case n: NullableType => boxedTypeArg(n.innerType)
              case other => boxedTypeArg(other)
            TypeSubstitution.substituteType(upper0, classSubst, scala.collection.immutable.Map(name -> self), defaultToBound = true)
          case _ => bound
        // A variable fixes an F-bound only through its own bound, by the rule on #2338:
        // `U extends Comparable[U]` meets `Comparable[U]`, `U extends Comparable[String]`
        // does not. Read by erasure, both would.
        // A bound that names another parameter (`T extends List[B]`) is read here with that
        // parameter erased (`List[Object]`), which refuses a valid `List[String]`; a fixed
        // parameter is checked against the bound with every argument known, below (#2538).
        val chained = fixedType.isDefined && tp.upperBound.exists(mentionsTypeParam(_, paramNames - name))
        def meetsBound(t: Type): Boolean = (selfBound, t) match
          case _ if chained => true
          case (applied: AppliedClassType, tv: TypeVariableType) if !(selfBound eq bound) =>
            TypeRelations.typeVariableFitsAppliedBound(applied, tv, typing.table_)
          case _ => isAssignableForBounds(selfBound, t)

        // Nullable inferences satisfy the bound through their inner type and
        // are rejected outright for non-null parameters (the Object top-type
        // rule would otherwise let String? through an Object bound)
        val boundsOk = inferredType0 match {
          case n: NullableType =>
            tp.nullability != Nullability.NonNull && meetsBound(n.innerType)
          case other =>
            meetsBound(other)
        }
        val inferredType =
          if (!boundsOk) {
            reportError(callNode, INCOMPATIBLE_TYPE, selfBound, inferredType0)
            failedBound += name
            bound
          } else {
            inferredType0
          }

        inferred += name -> inferredType
      }

      // A bound that names another of this method's type parameters
      // (`[B, T extends B]`) was checked above with that parameter erased to
      // its own bound. Re-check it now that every argument is known (#2325),
      // for a parameter something fixed (a defaulted one is its bound, as in
      // `mk[B, T extends B](b: B)`) and whose own bound check has not failed.
      val finalSubst = inferred.toMap
      for (tp <- typeParams; upper0 <- tp.upperBound
           if pinned(tp.name) && !failedBound(tp.name) && mentionsTypeParam(upper0, paramNames - tp.name)) {
        val upper = TypeSubstitution.substituteType(upper0, classSubst, finalSubst, defaultToBound = true)
        val arg = finalSubst(tp.name)
        val argInner = arg match {
          case n: NullableType => n.innerType
          case other => other
        }
        if (!isAssignableForBounds(upper, argInner)) reportError(callNode, INCOMPATIBLE_TYPE, upper, arg)
      }

      finalSubst
    }
  }
}
