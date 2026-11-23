package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*
import scala.util.boundary
import scala.util.boundary.break

/**
 * Re-types "malleable" call arguments against a resolved parameter's expected
 * type so a generic call in argument position can pin its type arguments.
 *
 * Arguments are normally typed before the target method is known ([[MethodCallTyping.typedTerms]]
 * takes no expected type), so a generic constructor/static call there leaves its
 * unconstrained type parameters unbound; they default to Object. For example
 * `Result::ok(7)` becomes `Result[Int, Object]`, which is not assignable to a
 * parameter `Result[Integer, String]` under invariant generics, so overload
 * resolution fails (issue #232).
 *
 * When the initial resolution finds no applicable method, this helper narrows
 * the candidates to a single one by name and arity, then re-types the malleable
 * arguments (a static/unqualified call, or a collection/map literal) against
 * that candidate's parameter types. The expected type — which may still carry
 * the candidate's own unbound type variables (e.g. `Result[T, String]`) — flows
 * into the argument's generic inference, pinning its known type arguments
 * (E = String) rather than leaving them to default to Object. This mirrors the
 * closure-body analog shipped for #230.
 *
 * The re-typing is best-effort and only touches the failure path: if it cannot
 * uniquely narrow the candidate or nothing changes, it returns the original
 * params so existing not-found / ambiguous diagnostics are preserved.
 */
private[compiler] final class ArgumentExpectedTypeRetyping(
  typing: Typing,
  calls: MethodCallTyping
) {

  /**
   * Attempt to re-type malleable arguments against a uniquely-determined
   * candidate's parameter types. Returns an updated params array only when a
   * single candidate matches by name/arity and at least one argument was
   * successfully re-typed to a different type; otherwise returns None.
   *
   * @param sourceType   type whose hierarchy provides the candidate methods
   * @param name         method name
   * @param args         the AST argument expressions
   * @param params       the arguments already typed without an expected type
   * @param context      current local context
   * @param filter       predicate selecting the relevant methods (static vs. instance)
   * @param receiverType type used to compute the class-level substitution for the
   *                     candidate (defaults to sourceType)
   */
  def retypeArguments(
    sourceType: ObjectType,
    name: String,
    args: Seq[AST.Expression],
    params: Array[Term],
    context: LocalContext,
    filter: Method => Boolean,
    receiverType: Type = null
  ): Option[Array[Term]] = {
    if (params == null || params.isEmpty) return None
    // Only worth trying when some argument is re-typeable to a more precise type.
    // A branching argument too: `f(if b { some(1) } else { none() })` gives `none()`
    // the parameter's type, and an `Option[String]` branch passed for an `Option[Int]`
    // is an error at that branch, as for a typed `val` (#3255).
    val malleableIndices = args.zipWithIndex.collect {
      case (expr, i) if i < params.length && (isMalleable(expr) || isBranching(expr)) => i
    }
    if (malleableIndices.isEmpty) return None

    val candidate = uniqueCandidate(sourceType, name, params.length, filter) match {
      case Some(c) => c
      case None => return None
    }

    val classSubst =
      if (receiverType != null) TypeSubstitution.classSubstitution(receiverType)
      else TypeSubstitution.classSubstitution(sourceType)
    // Leave the candidate's own method type variables in place (defaultToBound =
    // false) so the expected type can still pin an argument call's known type
    // arguments while an unbound variable (e.g. T in Result[T, String]) is
    // resolved by the argument itself.
    val expectedArgTypes = candidate.arguments.map { argType =>
      TypeSubstitution.substituteType(argType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
    }

    var changed = false
    val updated = params.clone()
    for (i <- malleableIndices if i < expectedArgTypes.length) {
      val expectedType = expectedArgTypes(i)
      if (expectedType != null && isBranching(args(i))) {
        if (!TypeCheckingHelpers.containsTypeVariable(expectedType)) {
          val errorsBefore = typing.suppressedErrorCount
          val retyped = retypeArgument(args(i), context, expectedType)
          val muted = typing.suppressedErrorCount != errorsBefore
          retyped match {
            case Some(term) if term.`type` != null && !(term.`type` eq params(i).`type`) =>
              // A branch that does not fit the only candidate's parameter is the one error:
              // report it there, and pass the call, so no E0005 names the join.
              updated(i) = if (muted) calls.typed(args(i), context, expectedType).getOrElse(term) else term
              changed = true
            case _ =>
          }
        }
      } else if (expectedType != null) {
        retypeArgument(args(i), context, expectedType) match {
          case Some(term) if term.`type` != null && !(term.`type` eq params(i).`type`) =>
            updated(i) = term
            changed = true
          case _ =>
        }
      }
    }
    if (changed) Some(updated) else None
  }

  /**
   * Recover from a malleable argument that failed outright (not merely typed
   * to the wrong type) with no expected type -- e.g. `new Leaf()` for a
   * zero-field generic case with nothing of its own to pin its type parameter
   * from, which reports E0066 and makes the whole argument list fail to type
   * before [[retypeArguments]] ever gets a chance to run. Types the
   * non-malleable arguments first (suppressed), narrows to a single
   * name/arity-matched candidate, then retypes every malleable argument
   * against that candidate's resolved parameter types. Returns None when a
   * non-malleable argument itself fails to type, no candidate uniquely
   * matches, or a malleable argument still fails against its resolved
   * expected type -- the caller then re-runs the original eager typing
   * unsuppressed to report the real diagnostic. Mirrors
   * `ConstructionTyping.resolveConstructorForMalleableArgs` for a method call.
   */
  def resolveArgsAfterFailure(
    sourceType: ObjectType,
    name: String,
    args: Seq[AST.Expression],
    context: LocalContext,
    filter: Method => Boolean,
    receiverType: Type = null
  ): Option[Array[Term]] = boundary {
    val argsArr = args.toArray
    val malleableIndices = argsArr.zipWithIndex.collect { case (e, i) if isMalleable(e) => i }.toSet
    if (malleableIndices.isEmpty) break(None)

    val prelim = new Array[Term](argsArr.length)
    for (i <- argsArr.indices if !malleableIndices.contains(i)) {
      retypeArgument(argsArr(i), context, null) match {
        case Some(t) => prelim(i) = t
        case None => break(None)
      }
    }

    val candidate = uniqueCandidate(sourceType, name, argsArr.length, filter) match {
      case Some(c) => c
      case None => break(None)
    }

    val classSubst =
      if (receiverType != null) TypeSubstitution.classSubstitution(receiverType)
      else TypeSubstitution.classSubstitution(sourceType)
    val expectedArgTypes = candidate.arguments.map { argType =>
      TypeSubstitution.substituteType(argType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
    }
    if (expectedArgTypes.length != argsArr.length) break(None)

    val updated = prelim.clone()
    for (i <- malleableIndices) {
      val expectedType = expectedArgTypes(i)
      if (expectedType == null) break(None)
      retypeArgument(argsArr(i), context, expectedType) match {
        case Some(t) => updated(i) = t
        case None => break(None)
      }
    }
    Some(updated)
  }

  /**
   * Re-types a lambda argument whose result is a generic call or literal with
   * nothing of its own to pin its type arguments, against the parameter type
   * that the call's target type and its other arguments fix (#3067).
   *
   * `o.fold(() -> Option::none(), (s) -> Option::some(s.length()))` assigned to
   * an `Option[Int]`: typed on its own, the first lambda is a
   * `Function0[Option[Object]]`, which binds `U = Option[Object]` before the
   * target type is seen. Left out of the inference, `U` comes from the target
   * (`Option[Int]`), and the lambda typed against `Function0[Option[Int]]`
   * gives `Option::none()` its type argument.
   *
   * Only with a target type, and only when that leaves no method type variable
   * in the lambda's parameter type; a re-type that fails, reports an error, or
   * changes nothing keeps the original argument, so its own diagnostics stand.
   */
  def retypeTargetTypedClosures(
    callNode: AST.Node,
    method: Method,
    args: Seq[AST.Expression],
    params: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    context: LocalContext,
    expected: Type
  ): Array[Term] = {
    if (expected == null || params == null || method.typeParameters.isEmpty || method.isVararg) return params
    if (params.length != method.argumentCount || args.length != params.length) return params
    // `Option[Int]?` pins `U` as `Option[Int]`, which then fits the nullable target.
    val nullableTarget = expected.isInstanceOf[NullableType]
    val target = expected match {
      case n: NullableType => n.innerType
      case t => t
    }
    if (target.isBasicType) return params
    val weak = args.indices.filter(i => params(i) != null && isTargetTypedClosure(args(i), nullableTarget))
    if (weak.isEmpty) return params
    val withoutWeak = params.clone()
    weak.foreach(withoutWeak(_) = null)
    val subst = typing.withSuppressedReporting {
      GenericMethodTypeArguments.inferWithoutDefaults(typing, callNode, method, withoutWeak, classSubst, target)
    }
    val names = method.typeParameters.map(_.name).toSet
    var updated: Array[Term] = null
    for (i <- weak) {
      val formal = TypeSubstitution.substituteType(method.arguments(i), classSubst, subst, defaultToBound = false)
      if (formal != null && !mentionsAny(formal, names)) {
        // A re-type that muted an error (a `return 42` in a lambda now typed to return
        // `Option[Int]`) is not kept: the original lambda reports it, as without the re-type.
        val errorsBefore = typing.suppressedErrorCount
        val retyped = retypeArgument(args(i), context, formal)
        retyped.filter(_ => typing.suppressedErrorCount == errorsBefore) match {
          case Some(term) if term.`type` != null && !(term.`type` eq params(i).`type`) &&
              TypeRules.isAssignable(formal, term.`type`) =>
            if (updated == null) updated = params.clone()
            updated(i) = term
          case _ if typing.suppressedErrorCount != errorsBefore && originalFitsSilently(callNode, method, params, classSubst, target) =>
            // The original lambda's results joined to a raw type (`Option[String]` and
            // `Option[Int]` -> `Option`) that fits the target, so it reports nothing and the
            // muted error is the only one there is: report it, at the `return` (#3117).
            calls.typed(args(i), context, formal)
          case _ if typing.suppressedErrorCount != errorsBefore && resultIsWildcardJoin(params(i).`type`) =>
            // The results joined to `Option[?]` (#3255), which the target rejects at the
            // call. The muted error is the precise one: report it at the branch, and pass
            // the call the re-typed lambda, so the call adds no second error.
            calls.typed(args(i), context, formal).foreach { term =>
              if (updated == null) updated = params.clone()
              updated(i) = term
            }
          case _ =>
        }
      }
    }
    if (updated == null) params else updated
  }

  /** Whether the call, with its arguments as first typed, yields a result the target accepts. */
  private def originalFitsSilently(
    callNode: AST.Node,
    method: Method,
    params: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    target: Type
  ): Boolean = {
    val errorsBefore = typing.suppressedErrorCount
    val subst = typing.withSuppressedReporting {
      GenericMethodTypeArguments.inferWithoutDefaults(typing, callNode, method, params, classSubst, target)
    }
    if (typing.suppressedErrorCount != errorsBefore) return false
    val result = TypeSubstitution.substituteType(method.returnType, classSubst, subst, defaultToBound = true)
    result != null && TypeRules.isAssignable(target, result)
  }

  /** A function type whose result is a join of different type arguments, such as `() -> Option[?]`. */
  private def resultIsWildcardJoin(t: Type): Boolean = t match {
    case fn: AppliedClassType if fn.typeArguments.nonEmpty =>
      fn.typeArguments.last match {
        case ap: AppliedClassType =>
          ap.typeArguments.nonEmpty && ap.typeArguments.forall {
            case w: WildcardType => w.lowerBound.isEmpty
            case _ => false
          }
        case _ => false
      }
    case _ => false
  }

  private def mentionsAny(t: Type, names: Set[String]): Boolean = t match {
    case tv: TypeVariableType => names.contains(tv.name)
    case applied: AppliedClassType => applied.typeArguments.exists(mentionsAny(_, names))
    case at: ArrayType => mentionsAny(at.base, names)
    case n: NullableType => mentionsAny(n.innerType, names)
    case w: WildcardType => mentionsAny(w.upperBound, names) || w.lowerBound.exists(mentionsAny(_, names))
    case _ => false
  }

  /**
   * A lambda that declares its parameter types (or has none) and no result type,
   * whose result is malleable (a generic call or a collection literal), or that has
   * an explicit `return`: its results join to a raw type (`Option[String]` and
   * `Option[Int]` -> `Option`) that fits the target whatever the last expression is,
   * so only the re-type checks each `return` against the target (#3234). The same
   * holds for a last expression that branches (#3243). Not for a
   * nullable target: the re-type drops its `?`, so a valid `return null` would warn W0012.
   */
  private def isTargetTypedClosure(expr: AST.Expression, nullableTarget: Boolean): Boolean = {
    val closure = expr match {
      case c: AST.ClosureExpression => c
      case AST.BlockExpression(_, List(c: AST.ClosureExpression), _) => c
      case _ => return false
    }
    closure.returns == null && closure.args.forall(_.typeRef != null) &&
      closure.body != null && closure.body.elements.nonEmpty && ((closure.body.elements.last match {
        case AST.ReturnExpression(_, result) => result != null && isMalleable(result)
        case e: AST.Expression => isMalleable(e)
        case _ => false
      }) || (!nullableTarget && (ReturnNodeDetector.containsReturn(closure.body) ||
        isBranching(closure.body.elements.last))))
  }

  /**
   * An `if`/`else`, `select` or `try` whose branches may join to a raw type (#3243):
   * `if b { other } else { tail }` with `Option[String]` and `Option[Int]` branches
   * joins to `Option`, which fits the target, so only the re-type checks each branch.
   */
  private def isBranching(e: AST.Node): Boolean = e match {
    case AST.IfExpression(_, _, _, elseBlock) => elseBlock != null
    case _: AST.SelectExpression => true
    case _: AST.TryExpression => true
    case _ => false
  }

  /** A single method (by name) whose arity accepts `argCount` arguments, else None. */
  private def uniqueCandidate(
    sourceType: ObjectType,
    name: String,
    argCount: Int,
    filter: Method => Boolean
  ): Option[Method] = {
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(sourceType, name, candidates, filter)
    val byArity = candidates.asScala.filter(acceptsArity(_, argCount)).toList
    byArity match {
      case single :: Nil => Some(single)
      case _ => None
    }
  }

  private def acceptsArity(method: Method, argCount: Int): Boolean =
    if (method.isVararg) argCount >= method.minArguments - 1
    else argCount >= method.minArguments && argCount <= method.argumentCount

  /** Re-type an argument expression against the expected parameter type. */
  private def retypeArgument(
    expr: AST.Expression,
    context: LocalContext,
    expectedType: Type
  ): Option[Term] =
    // Suppress diagnostics: this is a speculative re-type. If it fails, the
    // original resolution error is reported by the caller.
    typing.withSuppressedReporting {
      calls.typed(expr, context, expectedType)
    }

  /**
   * An argument whose inferred type can shift under an expected type: a generic
   * static/unqualified call (e.g. `Result::ok`, `Option::some`) or a collection
   * literal (whose element/entry types are target-typed). Non-generic arguments
   * and already-fixed values are left untouched.
   */
  private[typing] def isMalleable(expr: AST.Expression): Boolean = expr match {
    case _: AST.StaticMethodCall => true
    case _: AST.UnqualifiedMethodCall => true
    case _: AST.NewObject => true
    case _: AST.ListLiteral => true
    case _: AST.MapLiteral => true
    case _ => false
  }
}
