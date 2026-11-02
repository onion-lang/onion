package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

/**
 * `--contracts runtime` for `ensures` (RFC #2557, design 7.4): split a guarded method in two.
 *
 * The body keeps its frame and becomes a private method `m$body`. The method `m` becomes the guard:
 * check the `requires` clauses, call `m$body` with the same arguments, check the `ensures` clauses
 * on the result, and return it. Every `return` of the original body stays inside `m$body`, so none
 * can leave the method unchecked; rewriting each `return` was rejected because one statement form
 * missed would be a silent hole in a guard.
 *
 * Only a method this split is safe for is guarded: an instance method, not generic, not variadic,
 * with an `ensures` that does not use `old(...)` (saving the old values comes later). For any other
 * method with an `ensures` the clause is not enforced, and the warning W0026 says so.
 */
private[typing] object EnsuresGuardSupport {
  /** Why a method cannot be split, or none when it can. */
  def refusal(method: MethodDefinition, ensures: List[AST.Expression]): Option[String] =
    if ((method.modifier & AST.M_STATIC) != 0) Some("it is static")
    else if (method.typeParameters.nonEmpty) Some("it is generic")
    else if (method.isVararg) Some("it takes a variable number of arguments")
    else if (ensures.exists(mentionsOld)) Some("an ensures clause uses old(...), which is not saved at run time yet")
    else None

  private def mentionsOld(e: AST.Expression): Boolean = e match {
    case AST.UnqualifiedMethodCall(_, "old", _, _) => true
    case b: AST.BinaryExpression                  => mentionsOld(b.lhs) || mentionsOld(b.rhs)
    case u: AST.UnaryExpression                   => mentionsOld(u.term)
    case _                                        => false
  }
}
