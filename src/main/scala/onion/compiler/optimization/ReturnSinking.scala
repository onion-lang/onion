/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.optimization

import onion.compiler._
import onion.compiler.TypedAST._

/**
 * Sinks a `return` into the branches of an if-expression body, so a tail call inside
 * `= if c { f(..) } else { g(..) }` is a `return` the call-graph analysis can see (#3013).
 */
private[optimization] object ReturnSinking {
  // ── Return-sinking normalization ──────────────────────────────────────────
  // Converts `Return(Begin([StatementTerm(IfStatement(c, assign(v,t1), assign(v,t2))), RefLocal(v)]))`
  // (the TypedAST form of a non-void if-expression used as a return value) into
  // `IfStatement(c, Return(t1), Return(t2))` so that downstream analysis and
  // transformation can see the tail calls in each branch.

  def normalizeMethodBody(block: StatementBlock): StatementBlock = {
    val normalized = block.statements.map(normalizeStatement)
    new StatementBlock(block.location, normalized.toSeq: _*)
  }

  private def normalizeStatement(stmt: ActionStatement): ActionStatement = stmt match {
    case ret: Return if ret.term != null =>
      sinkReturn(ret.term).getOrElse(ret)
    case block: StatementBlock =>
      val ns = block.statements.map(normalizeStatement)
      new StatementBlock(block.location, ns.toSeq: _*)
    case ifStmt: IfStatement =>
      val normalizedThen = normalizeStatement(ifStmt.thenStatement)
      val normalizedElse = if (ifStmt.elseStatement != null) normalizeStatement(ifStmt.elseStatement) else null
      new IfStatement(ifStmt.location, ifStmt.condition, normalizedThen, normalizedElse)
    case _ => stmt
  }

  /** Try to expand Return(Begin(…if-expression…)) into IfStatement(c, Return(t1), Return(t2)).
   *  Returns None when the pattern does not match. */
  private def sinkReturn(term: Term): Option[ActionStatement] = term match {
    case begin: Begin if begin.terms.length == 2 =>
      (begin.terms(0), begin.terms(1)) match {
        case (stmtTerm: StatementTerm, refLocal: RefLocal) =>
          stmtTerm.statement match {
            case ifStmt: IfStatement if ifStmt.elseStatement != null =>
              val vFrame = refLocal.frame
              val vIdx   = refLocal.index
              val thenOpt = extractBranchValue(ifStmt.thenStatement, vFrame, vIdx)
              val elseOpt = extractBranchValue(ifStmt.elseStatement, vFrame, vIdx)
              (thenOpt, elseOpt) match {
                case (Some(thenVal), Some(elseVal)) =>
                  val loc         = ifStmt.location
                  val normalizedThen = makeReturnBranch(loc, thenVal)
                  val normalizedElse = makeReturnBranch(loc, elseVal)
                  Some(new IfStatement(loc, ifStmt.condition, normalizedThen, normalizedElse))
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    case _ => None
  }

  private def makeReturnBranch(loc: Location, value: Term): ActionStatement =
    sinkReturn(value).getOrElse(new StatementBlock(loc, new Return(loc, value)))

  /** Extract the value assigned to local variable (frame, index) from a branch statement. */
  private def extractBranchValue(stmt: ActionStatement, frame: Int, index: Int): Option[Term] = stmt match {
    case exprStmt: ExpressionActionStatement =>
      exprStmt.term match {
        case setLocal: SetLocal if setLocal.frame == frame && setLocal.index == index =>
          Some(setLocal.value)
        case _ => None
      }
    case block: StatementBlock if block.statements.length == 1 =>
      extractBranchValue(block.statements(0), frame, index)
    case _ => None
  }

  // ─────────────────────────────────────────────────────────────────────────
}
