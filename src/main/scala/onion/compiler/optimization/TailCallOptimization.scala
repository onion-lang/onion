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
import scala.collection.mutable

/**
 * Tail Call Optimization Phase
 *
 * Converts tail-recursive methods into loops to prevent stack overflow.
 *
 * == Transformation Strategy ==
 *
 * 1. Detect tail-recursive methods
 * 2. Wrap method body in while(true) loop
 * 3. Replace tail calls with parameter updates + continue
 *
 * Example:
 * {{{
 * // Before
 * def factorial(n: Int, acc: Int): Int {
 *   if (n <= 1) return acc
 *   return factorial(n - 1, n * acc)  // Tail call
 * }
 *
 * // After
 * def factorial(n: Int, acc: Int): Int {
 *   while (true) {
 *     if (n <= 1) return acc
 *     val n_next = n - 1
 *     val acc_next = n * acc
 *     n = n_next
 *     acc = acc_next
 *     // continue (loop restarts)
 *   }
 * }
 * }}}
 */
class TailCallOptimization(config: CompilerConfig)
  extends Processor[Seq[ClassDefinition], Seq[ClassDefinition]] {

  class OptimizationEnvironment
  type Environment = OptimizationEnvironment

  private inline def trace(message: => String): Unit =
    if (config.verbose) System.err.println(message)

  def newEnvironment(source: Seq[ClassDefinition]): Environment =
    new OptimizationEnvironment

  def processBody(
    source: Seq[ClassDefinition],
    environment: OptimizationEnvironment
  ): Seq[ClassDefinition] = {
    source.foreach { classDef =>
      classDef.methods.foreach {
        case methodDef: MethodDefinition =>
          if (isTailRecursive(methodDef)) {
            if (config.verbose) {
              trace(s"[TCO] Optimizing tail-recursive method: ${classDef.name}.${methodDef.name}")
            }
            optimizeMethod(methodDef)
          }
        case _ => // Skip other method types
      }
    }
    source
  }

  private def isTailRecursive(method: MethodDefinition): Boolean = {
    val block = method.getBlock
    if (block == null || block.statements.isEmpty) return false

    // Only optimize methods that cannot be overridden, so a self-call is never
    // dynamically dispatched to a subclass: private, static (invokestatic),
    // or final methods. Rewriting an overridable public method's body into a
    // loop would change its dispatch behavior.
    if (!isOptimizable(method)) {
      if (config.verbose) {
        trace(s"[TCO] Skipping overridable method: ${method.classType.name}.${method.name}")
      }
      return false
    }

    // Skip methods with @TailRecursive annotation (they may be part of mutual recursion)
    if (method.annotations.contains("TailRecursive")) {
      if (config.verbose) {
        trace(s"[TCO] Skipping @TailRecursive annotated method: ${method.classType.name}.${method.name}")
      }
      return false
    }

    val hasTailCall = containsTailCall(block.statements.toSeq, method)
    if (config.verbose) {
      trace(s"[TCO] Method ${method.classType.name}.${method.name}: hasTailCall=$hasTailCall")
    }
    hasTailCall
  }

  /**
   * A method is safe to convert to a loop when it cannot be overridden, so a
   * self-call is always this exact method: private, static, or final.
   */
  private def isOptimizable(method: MethodDefinition): Boolean = {
    val m = method.modifier
    (m & AST.M_PRIVATE) != 0 || (m & AST.M_STATIC) != 0 || (m & AST.M_FINAL) != 0
  }

  private def containsTailCall(stmts: Seq[ActionStatement], method: MethodDefinition): Boolean = {
    if (stmts.isEmpty) return false

    stmts.exists {
      case ret: Return if ret.term != null =>
        isSelfCall(ret.term, method)
      case block: StatementBlock =>
        containsTailCall(block.statements.toSeq, method)
      case ifStmt: IfStatement =>
        checkStatementForTailCall(ifStmt.thenStatement, method) ||
        (if (ifStmt.elseStatement != null) checkStatementForTailCall(ifStmt.elseStatement, method) else false)
      case _ =>
        false
    }
  }

  private def checkStatementForTailCall(stmt: ActionStatement, method: MethodDefinition): Boolean = {
    if (config.verbose) {
      trace(s"[TCO]   checkStatementForTailCall: ${stmt.getClass.getSimpleName}")
    }
    stmt match {
      case ret: Return =>
        val result = ret.term != null && isSelfCall(ret.term, method)
        if (config.verbose) {
          trace(s"[TCO]     Return statement: hasSelfCall=$result")
        }
        result
      case block: StatementBlock =>
        containsTailCall(block.statements.toSeq, method)
      case ifStmt: IfStatement =>
        if (config.verbose) {
          trace(s"[TCO]     IfStatement: checking then and else branches")
        }
        val thenResult = checkStatementForTailCall(ifStmt.thenStatement, method)
        val elseResult = if (ifStmt.elseStatement != null) checkStatementForTailCall(ifStmt.elseStatement, method) else false
        if (config.verbose) {
          trace(s"[TCO]     IfStatement: then=$thenResult, else=$elseResult")
        }
        thenResult || elseResult
      case exprStmt: ExpressionActionStatement =>
        // Check expression in tail position
        if (config.verbose) {
          trace(s"[TCO]     ExpressionActionStatement: checking term")
        }
        val result = isSelfCall(exprStmt.term, method)
        if (config.verbose) {
          trace(s"[TCO]     ExpressionActionStatement: hasSelfCall=$result")
        }
        result
      case _ =>
        if (config.verbose) {
          trace(s"[TCO]     Unknown statement type")
        }
        false
    }
  }

  private def isSelfCall(term: Term, method: MethodDefinition): Boolean = {
    if (config.verbose) {
      trace(s"[TCO]   Checking term type: ${term.getClass.getSimpleName}")
    }
    term match {
      case call: Call =>
        call.method match {
          case targetMethod: MethodDefinition =>
            // The receiver must be `this`: a matching name/class/signature call on
            // some other instance of the same class (e.g. `next.sumAcc(...)`) is not
            // self-recursion, and rewriting it into a loop would keep updating this
            // method's own locals while never advancing to the new receiver.
            val isSelf = call.target.isInstanceOf[This] &&
            targetMethod.name == method.name &&
            targetMethod.classType.name == method.classType.name &&
            argumentTypesMatch(targetMethod.arguments, method.arguments)
            if (config.verbose) {
              trace(s"[TCO]   Call to ${targetMethod.name}: isSelf=$isSelf")
            }
            isSelf
          case _ => false
        }
      case call: CallStatic =>
        call.method match {
          case targetMethod: MethodDefinition =>
            val isSelf = targetMethod.name == method.name &&
            call.target.name == method.classType.name &&
            argumentTypesMatch(targetMethod.arguments, method.arguments)
            if (config.verbose) {
              trace(s"[TCO]   CallStatic to ${targetMethod.name}: isSelf=$isSelf")
            }
            isSelf
          case _ => false
        }
      case stmtTerm: StatementTerm =>
        // Check if-else expression (wrapped in StatementTerm)
        if (config.verbose) {
          trace(s"[TCO]   StatementTerm wrapping: ${stmtTerm.statement.getClass.getSimpleName}")
        }
        checkStatementForTailCall(stmtTerm.statement, method)
      case begin: Begin =>
        // Check all terms in begin block for if-else expressions
        if (begin.terms.nonEmpty) {
          if (config.verbose) {
            trace(s"[TCO]   Begin with ${begin.terms.length} terms:")
            begin.terms.zipWithIndex.foreach { case (t, i) =>
              trace(s"[TCO]     Term $i: ${t.getClass.getSimpleName}")
            }
          }
          // Check all terms (if-else expressions may be in any position)
          begin.terms.exists(t => isSelfCall(t, method))
        } else {
          false
        }
      case setLocal: SetLocal =>
        // Check value being assigned (may contain tail call)
        if (config.verbose) {
          trace(s"[TCO]   SetLocal: checking value")
        }
        val result = isSelfCall(setLocal.value, method)
        if (config.verbose) {
          trace(s"[TCO]   SetLocal: value has self call=$result")
        }
        result
      case _ =>
        if (config.verbose) {
          trace(s"[TCO]   Unknown term type, not a self call")
        }
        false
    }
  }

  private def argumentTypesMatch(args1: Array[Type], args2: Array[Type]): Boolean = {
    if (args1.length != args2.length) return false
    args1.zip(args2).forall { case (t1, t2) => t1.name == t2.name }
  }

  /**
   * Scans an entire method body for the highest SetLocal/RefLocal index, giving
   * the upper bound of already-allocated TypedAST local variable slots.  Used as
   * a fallback when method.getFrame() is null (e.g. top-level synthetic methods).
   */
  private def findMaxLocalIndexInBody(block: StatementBlock): Int = {
    def inTerms(ts: Array[Term]): Int =
      if (ts.isEmpty) -1 else ts.toSeq.map(inTerm).max
    def inTerm(t: Term): Int = t match {
      case ref: RefLocal    => ref.index
      case set: SetLocal    => Math.max(set.index, inTerm(set.value))
      case call: Call       => Math.max(inTerm(call.target), inTerms(call.parameters))
      case call: CallStatic => inTerms(call.parameters)
      case safeCall: SafeCall => Math.max(inTerm(safeCall.target), inTerms(safeCall.parameters))
      case safeExt: SafeStaticExtensionCall => Math.max(inTerm(safeExt.target), inTerms(safeExt.parameters))
      case superCall: CallSuper => inTerms(superCall.params)
      case bin: BinaryTerm  => Math.max(inTerm(bin.lhs), inTerm(bin.rhs))
      case un: UnaryTerm    => inTerm(un.operand)
      case begin: Begin     => if (begin.terms.isEmpty) -1 else begin.terms.toSeq.map(inTerm).max
      case st: StatementTerm => inStmt(st.statement)
      case cast: AsInstanceOf => inTerm(cast.target)
      case refArr: RefArray => Math.max(inTerm(refArr.target), inTerm(refArr.index))
      case safeArr: SafeRefArray => Math.max(inTerm(safeArr.target), inTerm(safeArr.index))
      case setArr: SetArray => Math.max(inTerm(setArr.target), Math.max(inTerm(setArr.index), inTerm(setArr.value)))
      case refField: RefField => inTerm(refField.target)
      case safeField: SafeFieldAccess => inTerm(safeField.target)
      case setField: SetField => Math.max(inTerm(setField.target), inTerm(setField.value))
      case arrLen: ArrayLength => inTerm(arrLen.target)
      case inst: InstanceOf => inTerm(inst.target)
      case nonNull: NonNullAssert => inTerm(nonNull.target)
      case newObj: NewObject => inTerms(newObj.parameters)
      case newArr: NewArray => inTerms(newArr.parameters)
      case newArrVals: NewArrayWithValues => inTerms(newArrVals.values)
      case listLit: ListLiteral => inTerms(listLit.elements)
      case mapLit: MapLiteral => Math.max(inTerms(mapLit.keys), inTerms(mapLit.values))
      case _ => -1
    }
    def inStmt(s: ActionStatement): Int = s match {
      case ret: Return  => if (ret.term != null) inTerm(ret.term) else -1
      case sb: StatementBlock =>
        if (sb.statements.isEmpty) -1 else sb.statements.toSeq.map(inStmt).max
      case ifStmt: IfStatement =>
        val maxCond = inTerm(ifStmt.condition)
        val maxThen = inStmt(ifStmt.thenStatement)
        val maxElse = if (ifStmt.elseStatement != null) inStmt(ifStmt.elseStatement) else -1
        Math.max(maxCond, Math.max(maxThen, maxElse))
      case loop: ConditionalLoop =>
        val maxCond = inTerm(loop.condition)
        val maxBody = inStmt(loop.stmt)
        val maxUpd  = if (loop.update != null) inStmt(loop.update) else -1
        Math.max(maxCond, Math.max(maxBody, maxUpd))
      case exprStmt: ExpressionActionStatement => inTerm(exprStmt.term)
      case _ => -1
    }
    if (block.statements.isEmpty) -1
    else block.statements.toSeq.map(inStmt).max
  }

  /**
   * Where a tail call writes: each parameter's own slot (`paramTypes(i)` is parameter i's
   * type), and, for two or more parameters, the temp slots starting at `tempVarOffset`.
   */
  private final case class TcoSlots(paramTypes: IndexedSeq[Type], tempVarOffset: Int)

  /**
   * Optimizes a tail-recursive method by converting it to a loop.
   */
  private def optimizeMethod(method: MethodDefinition): Unit = {
    val block = method.getBlock
    val paramCount = method.arguments.length

    // A tail call stores the new arguments straight into the parameters' own slots
    // (the JVM allows writing to a parameter slot), so every existing read of a
    // parameter -- inside `try`, a lambda's capture, a static-field assignment, or any
    // other node -- sees the current iteration's value without being rewritten.
    // Copying the parameters into separate loop variables and rewriting the reads
    // missed every node kind the rewrite did not list, and those reads kept the
    // first call's value (#2329).
    //
    // With two or more parameters, the new arguments are first evaluated into temp
    // variables, so each one still sees the old parameter values (`f(n - 1, n * acc)`).
    // Temp slots must come after ALL locals already in the method's frame (parameters
    // + any body-declared locals like `val x`, `val next`, ...), or they collide with
    // them. For regular class methods the LocalFrame is populated during type-checking;
    // for top-level (synthetic) functions it may be null, so scan the body instead.
    val maxLocalIndex: Int =
      Option(method.getFrame).map(_.entries) match {
        case Some(entries) if entries.nonEmpty => entries.map(_.index).max
        case _ =>
          val bodyMax = findMaxLocalIndexInBody(block)
          Math.max(bodyMax, paramCount - 1)
      }
    val slots = TcoSlots(method.arguments.toIndexedSeq, maxLocalIndex + 1)
    if (paramCount >= 2) {
      method.setTcoLoopVars((0 until paramCount).map { i =>
        (slots.tempVarOffset + i, method.arguments(i))
      })
      trace(s"[TCO] Registered $paramCount temp variables for method ${method.name}")
    }

    val transformedBody = transformStatements(block.statements.toSeq, method, slots)

    // Remove unreachable return statements at the end
    val cleanedBody = removeTrailingUnreachableReturns(transformedBody)

    // Wrap in infinite loop: while (true) { ... }
    val trueValue = new BoolValue(null, true)
    val loopBody = new StatementBlock(null, cleanedBody: _*)
    val loop = new ConditionalLoop(null, trueValue, loopBody)
    method.setBlock(new StatementBlock(null, Seq(loop): _*))
  }

  /**
   * Removes unreachable return statements from the end of a statement sequence.
   * These are typically added by the type checker as default returns.
   */
  private def removeTrailingUnreachableReturns(stmts: Seq[ActionStatement]): Seq[ActionStatement] = {
    if (stmts.isEmpty) return stmts

    // Check if the last statement is an unreachable return
    // (a return that follows a statement containing a tail call)
    stmts.last match {
      case _: Return =>
        // If there are previous statements and the last one contains returns,
        // this return is likely unreachable
        if (stmts.length > 1) {
          stmts.init  // Remove the last return
        } else {
          stmts  // Keep it if it's the only statement
        }
      case _ =>
        stmts
    }
  }

  /**
   * Transforms statements to replace tail calls with parameter updates.
   */
  private def transformStatements(
    stmts: Seq[ActionStatement],
    method: MethodDefinition,
    slots: TcoSlots
  ): Seq[ActionStatement] = {
    stmts.map { stmt =>
      transformStatement(stmt, method, slots)
    }
  }

  /**
   * Transforms a single statement, replacing tail calls.
   */
  private def transformStatement(
    stmt: ActionStatement,
    method: MethodDefinition,
    slots: TcoSlots
  ): ActionStatement = {
    stmt match {
      case ret: Return if ret.term != null && isSelfCall(ret.term, method) =>
        trace(s"[TCO transformStatement] Return with tail call, term type=${ret.term.getClass.getSimpleName}")
        // Tail call found
        // Check if it's wrapped in Begin with IfStatement
        ret.term match {
          case begin: Begin if begin.terms.nonEmpty =>
            trace(s"[TCO transformStatement] Return: Begin with ${begin.terms.length} terms")
            // Look for IfStatement in Begin
            val ifStmtOpt = begin.terms.collectFirst {
              case stmtTerm: StatementTerm =>
                stmtTerm.statement match {
                  case ifStmt: IfStatement => Some(ifStmt)
                  case _ => None
                }
            }.flatten

            trace(s"[TCO transformStatement] Return: ifStmtOpt = ${ifStmtOpt.isDefined}")
            ifStmtOpt match {
              case Some(ifStmt) =>
                trace(s"[TCO transformStatement] Return: Transforming IfStatement branches")

                // Get the result variable from the last term of Begin (RefLocal)
                val resultTerm = begin.terms.last match {
                  case refLocal: RefLocal => refLocal
                  case other =>
                    trace(s"[TCO transformStatement] Warning: Begin's last term is not RefLocal but ${other.getClass.getSimpleName}")
                    other
                }

                // Transform the branches
                // For branches without tail call, we need to add Return(resultTerm)
                // For branches with tail call, transformStatement will add Continue

                // Helper to add Return if branch doesn't end with Continue
                def ensureProperBranchEnd(transformedBranch: ActionStatement, branchName: String): ActionStatement = {
                  trace(s"[TCO ensureProperBranchEnd] $branchName: input type=${transformedBranch.getClass.getSimpleName}")
                  val result = transformedBranch match {
                    case block: StatementBlock if block.statements.nonEmpty =>
                      trace(s"[TCO ensureProperBranchEnd] $branchName: StatementBlock with ${block.statements.length} statements")
                      block.statements.zipWithIndex.foreach { case (stmt, i) =>
                        trace(s"[TCO ensureProperBranchEnd]   stmt[$i]: ${stmt.getClass.getSimpleName}")
                      }
                      // Check if last statement is Continue
                      block.statements.last match {
                        case _: Continue =>
                          trace(s"[TCO ensureProperBranchEnd] $branchName: Already has Continue")
                          // Already has Continue, keep as-is
                          block
                        case _ =>
                          trace(s"[TCO ensureProperBranchEnd] $branchName: Adding Return")
                          // No Continue, add Return
                          val stmtsWithReturn = block.statements.toSeq :+ new Return(null, resultTerm)
                          new StatementBlock(block.location, stmtsWithReturn: _*)
                      }
                    case other =>
                      trace(s"[TCO ensureProperBranchEnd] $branchName: Single statement, wrapping with Return")
                      // Single statement (ExpressionActionStatement, etc.)
                      // Wrap in block with Return
                      new StatementBlock(null, Seq(other, new Return(null, resultTerm)): _*)
                  }
                  trace(s"[TCO ensureProperBranchEnd] $branchName: result type=${result.getClass.getSimpleName}")
                  result
                }

                val transformedThen = ensureProperBranchEnd(transformStatement(ifStmt.thenStatement, method, slots), "then")
                val transformedElse = if (ifStmt.elseStatement != null) {
                  ensureProperBranchEnd(transformStatement(ifStmt.elseStatement, method, slots), "else")
                } else null

                // Return the transformed IfStatement directly
                new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)
              case None =>
                trace(s"[TCO transformStatement] Return: No IfStatement found, calling transformTailCall")
                // No IfStatement found, transform as direct call
                transformTailCall(ret.term, slots).getOrElse(ret)
            }
          case stmtTerm: StatementTerm =>
            // Explicit return: Return(StatementTerm(IfStatement))
            trace(s"[TCO transformStatement] Return: StatementTerm wrapping ${stmtTerm.statement.getClass.getSimpleName}")
            stmtTerm.statement match {
              case ifStmt: IfStatement =>
                trace(s"[TCO transformStatement] Return: Transforming explicit return IfStatement")
                // IfStatementの両ブランチをtransformStatementで処理
                val transformedThen = transformStatement(ifStmt.thenStatement, method, slots)
                val transformedElse = if (ifStmt.elseStatement != null) {
                  transformStatement(ifStmt.elseStatement, method, slots)
                } else null
                new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)
              case _ =>
                // Other StatementTerms: fallback to transformTailCall
                transformTailCall(ret.term, slots).getOrElse(ret)
            }

          case _ =>
            trace(s"[TCO transformStatement] Return: Not Begin, calling transformTailCall")
            // Direct tail call (not in Begin)
            transformTailCall(ret.term, slots).getOrElse(ret)
        }

      case block: StatementBlock =>
        // Recursively transform statements in block
        val transformed = transformStatements(block.statements.toSeq, method, slots)
        new StatementBlock(block.location, transformed: _*)

      case ifStmt: IfStatement =>
        // Transform both branches
        val transformedThen = transformStatement(ifStmt.thenStatement, method, slots)
        val transformedElse = if (ifStmt.elseStatement != null) {
          transformStatement(ifStmt.elseStatement, method, slots)
        } else null

        new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)

      case exprStmt: ExpressionActionStatement =>
        // An ExpressionActionStatement whose value is assigned to a local variable
        // (SetLocal) is NEVER a tail call: the result of the call is stored in the
        // variable and consumed by later code. Transforming it as a tail call would
        // incorrectly replace `val lr = self(args)` with a loop-variable update and
        // `continue`, skipping all subsequent statements in the block.
        // Keep every ExpressionActionStatement as-is.
        exprStmt

      case _ =>
        // Keep as-is
        stmt
    }
  }

  /**
   * Transforms a tail call into parameter assignments.
   *
   * For `return method(arg1, arg2, ...)`:
   * 1. temp1 = arg1; temp2 = arg2; ... (evaluate all new values first)
   * 2. param1 = temp1; param2 = temp2; ... (update the parameters)
   * 3. (continue - loop restarts)
   *
   * We use temporary variables to ensure correct evaluation order.
   * Example: factorial(n-1, n*acc) must evaluate both args with old n value.
   */
  private def transformTailCall(
    term: Term,
    slots: TcoSlots
  ): Option[ActionStatement] = {
    // Extract the actual Call from wrapped structures (Begin, StatementTerm, etc.)
    def extractCall(t: Term, depth: Int = 0): Option[Term] = {
      val indent = "  " * depth
      trace(s"$indent[TCO extractCall] depth=$depth, term type=${t.getClass.getSimpleName}")

      t match {
        case c: Call =>
          trace(s"$indent[TCO extractCall] Found Call!")
          Some(c)
        case c: CallStatic =>
          trace(s"$indent[TCO extractCall] Found CallStatic!")
          Some(c)
        case begin: Begin =>
          trace(s"$indent[TCO extractCall] Begin with ${begin.terms.length} terms:")
          begin.terms.zipWithIndex.foreach { case (term, i) =>
            trace(s"$indent  Term $i: ${term.getClass.getSimpleName}")
          }
          // If-else expression: recursively check all terms
          begin.terms.flatMap(t => extractCall(t, depth + 1)).headOption
        case stmtTerm: StatementTerm =>
          trace(s"$indent[TCO extractCall] StatementTerm wrapping ${stmtTerm.statement.getClass.getSimpleName}")
          // Unwrap StatementTerm
          stmtTerm.statement match {
            case exprStmt: ExpressionActionStatement =>
              extractCall(exprStmt.term, depth + 1)
            case _: IfStatement =>
              trace(s"$indent[TCO extractCall] IfStatement - should have been handled in transformStatement")
              None
            case _ => None
          }
        case setLocal: SetLocal =>
          trace(s"$indent[TCO extractCall] SetLocal, checking value")
          // Check value being assigned
          extractCall(setLocal.value, depth + 1)
        case other =>
          trace(s"$indent[TCO extractCall] Unhandled type: ${other.getClass.getSimpleName}")
          None
      }
    }

    val actualCallOpt = extractCall(term)
    if (actualCallOpt.isEmpty) {
      trace(s"[TCO transformTailCall] Could not extract Call from term type=${term.getClass.getSimpleName}, skipping TCO")
      return None
    }
    val actualCall = actualCallOpt.get

    val newArgs: Array[Term] = actualCall match {
      case call: Call => call.parameters
      case call: CallStatic => call.parameters
      case _ => Array.empty
    }

    if (config.verbose) {
      trace(s"[TCO]   transformTailCall: term type=${term.getClass.getSimpleName}, actualCall type=${actualCall.getClass.getSimpleName}, newArgs.length=${newArgs.length}")
    }

    val assignments = mutable.ArrayBuffer[ActionStatement]()

    if (newArgs.length == 0) {
      // Zero-argument tail call: restart the loop with current variable values
      trace(s"[TCO transformTailCall] Zero-argument tail call, emitting Continue")
      return Some(new Continue(null))
    } else if (newArgs.length == 1) {
      // Single argument: assign the parameter directly (no temp needed)
      val paramType = slots.paramTypes(0)
      assignments += new ExpressionActionStatement(null, new SetLocal(null, 0, 0, paramType, newArgs(0)))
    } else {
      // Multiple arguments: evaluate all of them into temps first, so each one sees the
      // old parameter values, then copy the temps into the parameters.
      trace(s"[TCO transformTailCall] Multiple arguments: ${newArgs.length}")
      newArgs.zipWithIndex.foreach { case (arg, i) =>
        val tempAssignment = new SetLocal(null, 0, slots.tempVarOffset + i, slots.paramTypes(i), arg)
        assignments += new ExpressionActionStatement(null, tempAssignment)
      }
      slots.paramTypes.zipWithIndex.foreach { case (paramType, i) =>
        val tempRef = new RefLocal(null, 0, slots.tempVarOffset + i, paramType)
        assignments += new ExpressionActionStatement(null, new SetLocal(null, 0, i, paramType, tempRef))
      }
    }

    // Add continue statement to restart the loop
    assignments += new Continue(null)

    // Return a block with all assignments and continue
    Some(new StatementBlock(null, assignments.toSeq: _*))
  }
}
