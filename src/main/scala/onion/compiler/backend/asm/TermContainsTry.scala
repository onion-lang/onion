package onion.compiler.backend.asm

import onion.compiler.TypedAST.*

/**
 * Detects whether evaluating a term may run its own `try`/`catch` machinery (a
 * nested [[Try]] statement), without crossing into a closure body (a closure
 * compiles to its own method with its own fresh operand stack, so a `try`
 * inside one cannot disturb the enclosing method's stack).
 *
 * The JVM clears the operand stack when it dispatches to an exception
 * handler, so any value already pushed on the stack *before* such a term is
 * evaluated would be silently discarded if the term's try block throws
 * (issue #669: `f(a, try { ... } catch { ... }, c)` corrupts the stack slot
 * holding `a`). Codegen uses this to know when it must spill already-pushed
 * operands into locals before evaluating a sibling operand, then reload them.
 */
private[asm] object TermContainsTry:

  def contains(term: Term): Boolean =
    onion.compiler.TermWalk.exists(term) { case _: Try => true; case _ => false }
