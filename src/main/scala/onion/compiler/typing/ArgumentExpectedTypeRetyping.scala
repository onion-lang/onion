package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

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
    val malleableIndices = args.zipWithIndex.collect {
      case (expr, i) if i < params.length && isMalleable(expr) => i
    }
    if (malleableIndices.isEmpty) return None

    val candidate = uniqueCandidate(sourceType, name, params.length, filter).getOrElse(return None)

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
      if (expectedType != null) {
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
    else argCount >= method.minArguments && argCount <= method.arguments.length

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
  private def isMalleable(expr: AST.Expression): Boolean = expr match {
    case _: AST.StaticMethodCall => true
    case _: AST.UnqualifiedMethodCall => true
    case _: AST.NewObject => true
    case _: AST.ListLiteral => true
    case _: AST.MapLiteral => true
    case _ => false
  }
}
