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

  def contains(term: Term): Boolean = term match
    case _: NewClosure => false
    case stmt: StatementTerm => containsStatement(stmt.statement)
    case other =>
      productChildren(other).exists {
        case t: Term => contains(t)
        case s: ActionStatement => containsStatement(s)
        case _ => false
      }

  private def containsStatement(statement: ActionStatement): Boolean = statement match
    case _: Try => true
    case other =>
      productChildren(other).exists {
        case t: Term => contains(t)
        case s: ActionStatement => containsStatement(s)
        case _ => false
      }

  // TypedAST terms/statements are plain classes, not case classes, so walk
  // their public accessor methods that return Terms/Statements/collections
  // (mirrors onion.compiler.backend.asm.CapturedVariableCollector).
  private def productChildren(node: AnyRef): Seq[AnyRef] = node match
    case p: Product => p.productIterator.collect { case r: AnyRef => r }.toSeq
    case _ => reflectiveChildren(node)

  private def reflectiveChildren(node: AnyRef): Seq[AnyRef] =
    node.getClass.getMethods.iterator
      .filter(m => m.getParameterCount == 0 && !m.getName.contains("$") &&
        (classOf[Term].isAssignableFrom(m.getReturnType) ||
         classOf[ActionStatement].isAssignableFrom(m.getReturnType) ||
         m.getReturnType.isArray ||
         classOf[java.util.List[?]].isAssignableFrom(m.getReturnType)))
      .flatMap { m =>
        try
          m.invoke(node) match
            case null => Nil
            case arr: Array[?] => arr.toSeq.collect { case r: AnyRef => r }
            case list: java.util.List[?] => scala.jdk.CollectionConverters.ListHasAsScala(list).asScala.toSeq.collect { case r: AnyRef => r }
            case other: AnyRef => Seq(other)
        catch case _: Throwable => Nil
      }.toSeq
