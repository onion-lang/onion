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

  def contains(term: Term): Boolean = containsNode(term)

  // Codegen asks this for the right-hand side of every assignment and for every call
  // argument, at every nesting level, so a plain walk revisited the same subterms once per
  // enclosing operand. The answer is memoized per node for the duration of one code
  // generation (see `reset`), and computed bottom-up so a nested question is a lookup.
  private val memo = new ThreadLocal[java.util.IdentityHashMap[AnyRef, java.lang.Boolean]] {
    override def initialValue() = new java.util.IdentityHashMap[AnyRef, java.lang.Boolean]()
  }

  /** Forgets the answers of the previous code generation. */
  def reset(): Unit = memo.get.clear()

  private def containsNode(node: AnyRef): Boolean =
    val m = memo.get
    val cached = m.get(node)
    if cached != null then cached.booleanValue
    else
      val result = node match
        case _: Try => true
        case _: NewClosure => false
        case stmt: StatementTerm => containsNode(stmt.statement)
        case other =>
          onion.compiler.TermWalk.children(other).exists {
            case t: Term => containsNode(t)
            case s: ActionStatement => containsNode(s)
            case _ => false
          }
      m.put(node, java.lang.Boolean.valueOf(result))
      result
