package onion.compiler.typing

import onion.compiler.TypedAST.{ClassType, Method, Term, Type}
import onion.compiler.AST

/**
 * Pattern binding information for pattern matching in select expressions.
 *
 * Represents the bindings that need to be created when a pattern matches:
 * - NoBindings: Pattern introduces no new bindings (e.g., literal patterns)
 * - SingleBinding: Pattern binds a single variable (e.g., `case x: Int`)
 * - MultiBindings: Pattern uses destructuring (e.g., `case Point(x, y)`)
 */
private[typing] sealed trait PatternBindingInfo

private[typing] case object NoBindings extends PatternBindingInfo

private[typing] case class SingleBinding(name: String, bindingType: Type) extends PatternBindingInfo

/**
 * Represents a step in the accessor path for destructuring patterns.
 * Each step specifies a cast type and a getter method to call.
 */
private[typing] case class AccessStep(castType: ClassType, getter: Method)

/**
 * An entry in a destructuring binding: variable name, type, and access path.
 */
private[typing] case class BindingEntry(name: String, bindingType: Type, accessPath: List[AccessStep])

/**
 * Bindings for a destructuring pattern with multiple variables.
 *
 * @param rootType The type of the matched value
 * @param bindings List of variable bindings with their access paths
 * @param nestedConditions Additional conditions from nested type checks
 */
private[typing] case class MultiBindings(
  rootType: ClassType,
  bindings: List[BindingEntry],
  nestedConditions: List[Term] = Nil
) extends PatternBindingInfo

/**
 * Guard expression info for pattern matching.
 *
 * @param guardAst The AST expression for validation
 * @param guardTerm The typed term for code generation (created in binding scope)
 */
private[typing] case class GuardInfo(guardAst: AST.Expression, guardTerm: Term = null)
