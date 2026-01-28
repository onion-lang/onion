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
    val methodByName = methods.map(m => m.name -> m).toMap

    methods.map { method =>
      val calledMethods = findTailCalls(method).flatMap { callName =>
        methodByName.get(callName)
      }.toSet
      method -> calledMethods
    }.toMap
  }

  /**
   * Find method names called in tail position within a method.
   *
   * A tail call is a method call that appears directly in a return statement,
   * with no further computation after the call returns.
   */
  def findTailCalls(method: MethodDefinition): Set[String] = {
    val calls = mutable.Set[String]()

    def visitStatement(stmt: ActionStatement): Unit = {
      stmt match {
        case ret: Return if ret.term != null =>
          ret.term match {
            case call: Call =>
              call.method match {
                case targetMethod: MethodDefinition =>
                  calls += targetMethod.name
                case _ =>
              }
            case call: CallStatic =>
              call.method match {
                case targetMethod: MethodDefinition =>
                  calls += targetMethod.name
                case _ =>
              }
            case _ =>
          }

        case block: StatementBlock =>
          block.statements.foreach(visitStatement)

        case ifStmt: IfStatement =>
          visitStatement(ifStmt.thenStatement)
          if (ifStmt.elseStatement != null) {
            visitStatement(ifStmt.elseStatement)
          }

        case _ =>
      }
    }

    if (method.getBlock != null) {
      method.getBlock.statements.foreach(visitStatement)
    }

    calls.toSet
  }
}
