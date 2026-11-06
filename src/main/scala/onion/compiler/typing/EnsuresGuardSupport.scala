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
 * Every method with a body can be split: an instance or a static method, generic or not, variadic or
 * not. `m$body` has the same type parameters and the same variable-arity flag, so a call passes the
 * arguments through unchanged. `refusal` stays as the one place to say why a method cannot be, should a
 * kind of method need it; today it never refuses, and the warning W0026 only names a clause that could
 * not be typed.
 */
private[typing] object EnsuresGuardSupport {
  /** Why a method cannot be split, or none when it can. */
  def refusal(method: MethodDefinition, ensures: List[AST.Expression]): Option[String] =
    None
}
