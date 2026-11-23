/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.optimization

import onion.compiler.TypedAST._
import scala.collection.mutable

/**
 * Tail call graph analysis for mutual recursion optimization.
 *
 * Builds a call graph showing which methods call which other methods
 * in tail position (i.e., return statements that directly return a method call).
 */
private[optimization] object TailCallGraphAnalysis {

  /**
   * Build call graph: method -> set of methods it calls in tail position
   *
   * @param methods Methods to analyze
   * @return Map from each method to the set of methods it tail-calls
   */
  def buildCallGraph(methods: Seq[MethodDefinition]): Map[MethodDefinition, Set[MethodDefinition]] = {
    // Overloads share a name, so members are told apart by identity, not by name.
    methods.map { method =>
      method -> findTailCalls(method).filter(callee => methods.exists(_ eq callee))
    }.toMap
  }

  /**
   * Find methods called in tail position within a method.
   *
   * A tail call is a method call that appears directly in a return statement,
   * with no further computation after the call returns.
   */
  def findTailCalls(method: MethodDefinition): Set[MethodDefinition] = {
    val calls = mutable.Set[MethodDefinition]()

    val isVoid = method.returnType eq BasicType.VOID

    // `tail` is whether nothing runs after `stmt` in the method (#3131).
    def visitStatements(stmts: Seq[ActionStatement], tail: Boolean): Unit =
      stmts.zipWithIndex.foreach { case (stmt, i) =>
        visitStatement(stmt, tail && endsTail(stmts, i + 1))
      }

    def visitStatement(stmt: ActionStatement, tail: Boolean): Unit = {
      stmt match {
        case bare if isVoid && tail && bareCall(bare).isDefined =>
          bareCall(bare).foreach(calls += _)

        case ret: Return if ret.term != null =>
          groupCall(ret.term).foreach { case (targetMethod, _) => calls += targetMethod }

        case block: StatementBlock =>
          visitStatements(block.statements.toSeq, tail)

        case ifStmt: IfStatement =>
          visitStatement(ifStmt.thenStatement, tail)
          if (ifStmt.elseStatement != null) {
            visitStatement(ifStmt.elseStatement, tail)
          }

        case _ =>
      }
    }

    if (method.getBlock != null) {
      visitStatements(ReturnSinking.normalizeMethodBody(method.getBlock).statements.toSeq, tail = true)
    }

    calls.toSet
  }

  /**
   * In a `void` method a tail call is a bare statement `f(..)`, not `return f(..)`: the
   * method it calls, when the statement is one (#3131).
   */
  def bareCall(stmt: ActionStatement): Option[MethodDefinition] = stmt match {
    case e: ExpressionActionStatement => groupCall(e.term).map(_._1)
    case _                            => None
  }

  /**
   * A call that can become a jump inside the group: a static call, or an instance call
   * whose receiver is `this`. The merged loop runs on `this`, so a call through another
   * object (`mk().b(n)`) would drop the receiver, its side effects and its null check
   * (#3251). Returns the callee and the arguments.
   */
  def groupCall(term: Term): Option[(MethodDefinition, Array[Term])] = term match {
    case c: Call if c.target.isInstanceOf[This] =>
      c.method match { case m: MethodDefinition => Some((m, c.parameters)); case _ => None }
    case c: CallStatic =>
      c.method match { case m: MethodDefinition => Some((m, c.parameters)); case _ => None }
    case _ => None
  }

  /** Whether the statements from `from` on do nothing: none, or only a bare `return`. */
  def endsTail(stmts: Seq[ActionStatement], from: Int): Boolean =
    stmts.drop(from).forall {
      case r: Return => r.term == null
      case _         => false
    }
}
