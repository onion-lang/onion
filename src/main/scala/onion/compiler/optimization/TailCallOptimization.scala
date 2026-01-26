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
              System.err.println(s"[TCO] Optimizing tail-recursive method: ${classDef.name}.${methodDef.name}")
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

    // Only optimize private methods to avoid breaking public API
    if (!isPrivate(method)) return false

    // Skip methods with @TailRecursive annotation (they may be part of mutual recursion)
    if (method.annotations.contains("TailRecursive")) return false

    containsTailCall(block.statements.toSeq, method)
  }

  /**
   * Check if method is private
   */
  private def isPrivate(method: MethodDefinition): Boolean = {
    // Use Onion's M_PRIVATE constant, not Java's Modifier.PRIVATE
    (method.modifier & AST.M_PRIVATE) != 0
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
    stmt match {
      case ret: Return =>
        ret.term != null && isSelfCall(ret.term, method)
      case block: StatementBlock =>
        containsTailCall(block.statements.toSeq, method)
      case ifStmt: IfStatement =>
        checkStatementForTailCall(ifStmt.thenStatement, method) ||
        (if (ifStmt.elseStatement != null) checkStatementForTailCall(ifStmt.elseStatement, method) else false)
      case _ =>
        false
    }
  }

  private def isSelfCall(term: Term, method: MethodDefinition): Boolean = {
    term match {
      case call: Call =>
        call.method match {
          case targetMethod: MethodDefinition =>
            targetMethod.name == method.name &&
            targetMethod.classType.name == method.classType.name &&
            argumentTypesMatch(targetMethod.arguments, method.arguments)
          case _ => false
        }
      case call: CallStatic =>
        call.method match {
          case targetMethod: MethodDefinition =>
            targetMethod.name == method.name &&
            call.target.name == method.classType.name &&
            argumentTypesMatch(targetMethod.arguments, method.arguments)
          case _ => false
        }
      case _ => false
    }
  }

  private def argumentTypesMatch(args1: Array[Type], args2: Array[Type]): Boolean = {
    if (args1.length != args2.length) return false
    args1.zip(args2).forall { case (t1, t2) => t1.name == t2.name }
  }

  /**
   * Optimizes a tail-recursive method by converting it to a loop.
   */
  private def optimizeMethod(method: MethodDefinition): Unit = {
    val block = method.getBlock
    val paramCount = method.arguments.length

    // In TypedAST, parameters always start at index 0, regardless of static/instance
    // The 'this' reference is not part of the LocalFrame indexing
    // JVM bytecode generation (LocalVarContext) handles the this/parameter slot mapping

    // Loop variable indices come after parameters
    // For method countdown(n: Int):
    //   TypedAST locals[0]: n (parameter)
    //   TypedAST locals[1]: n_loop (loop variable)
    //   TypedAST locals[2]: n_temp (temporary variable)
    val loopVarOffset = paramCount
    val loopVarMapping = (0 until paramCount).map { i =>
      val paramIndex = i
      val loopVarIndex = loopVarOffset + i
      val paramType = method.arguments(i)
      (paramIndex, loopVarIndex, paramType)
    }.toIndexedSeq

    // Step 1: Create initialization statements (copy parameters to loop variables)
    val initStatements = loopVarMapping.map { case (paramIndex, loopVarIndex, paramType) =>
      val paramRef = new RefLocal(null, 0, paramIndex, paramType)
      val loopVarAssignment = new SetLocal(null, 0, loopVarIndex, paramType, paramRef)
      new ExpressionActionStatement(null, loopVarAssignment)
    }

    // Step 2: Rewrite all parameter references in the method body to use loop variables
    val rewrittenBody = rewriteParameterReferences(
      block.statements.toSeq,
      loopVarMapping
    )

    // Step 3: Transform tail calls to loop variable updates
    val transformedBody = transformStatements(
      rewrittenBody,
      method,
      loopVarMapping
    )

    // Remove unreachable return statements at the end
    val cleanedBody = removeTrailingUnreachableReturns(transformedBody)

    // Wrap in infinite loop: while (true) { ... }
    val trueValue = new BoolValue(null, true)
    val loopBody = new StatementBlock(null, cleanedBody: _*)
    val loop = new ConditionalLoop(null, trueValue, loopBody)

    // Update method body: init statements + loop
    val newStatements = initStatements :+ loop
    val newBlock = new StatementBlock(null, newStatements: _*)
    method.setBlock(newBlock)
  }

  /**
   * Rewrites all parameter references in the method body to use loop variables.
   *
   * This is necessary because JVM method parameters are read-only.
   * We copy parameters to loop variables at method entry and use those throughout.
   */
  private def rewriteParameterReferences(
    stmts: Seq[ActionStatement],
    loopVarMapping: IndexedSeq[(Int, Int, Type)]
  ): Seq[ActionStatement] = {
    stmts.map(stmt => rewriteStatementRefs(stmt, loopVarMapping))
  }

  private def rewriteStatementRefs(
    stmt: ActionStatement,
    loopVarMapping: IndexedSeq[(Int, Int, Type)]
  ): ActionStatement = {
    stmt match {
      case ret: Return if ret.term != null =>
        new Return(ret.location, rewriteTermRefs(ret.term, loopVarMapping))

      case block: StatementBlock =>
        val rewritten = block.statements.toSeq.map(s => rewriteStatementRefs(s, loopVarMapping))
        new StatementBlock(block.location, rewritten: _*)

      case ifStmt: IfStatement =>
        val rewrittenCond = rewriteTermRefs(ifStmt.condition, loopVarMapping)
        val rewrittenThen = rewriteStatementRefs(ifStmt.thenStatement, loopVarMapping)
        val rewrittenElse = if (ifStmt.elseStatement != null) {
          rewriteStatementRefs(ifStmt.elseStatement, loopVarMapping)
        } else null
        new IfStatement(ifStmt.location, rewrittenCond, rewrittenThen, rewrittenElse)

      case exprStmt: ExpressionActionStatement =>
        new ExpressionActionStatement(exprStmt.location, rewriteTermRefs(exprStmt.term, loopVarMapping))

      case loop: ConditionalLoop =>
        val rewrittenCond = rewriteTermRefs(loop.condition, loopVarMapping)
        val rewrittenStmt = rewriteStatementRefs(loop.stmt, loopVarMapping)
        new ConditionalLoop(loop.location, rewrittenCond, rewrittenStmt)

      case _ =>
        // For other statement types, return as-is
        stmt
    }
  }

  private def rewriteTermRefs(
    term: Term,
    loopVarMapping: IndexedSeq[(Int, Int, Type)]
  ): Term = {
    term match {
      case ref: RefLocal =>
        // Check if this references a parameter
        loopVarMapping.find(_._1 == ref.index) match {
          case Some((_, loopVarIndex, paramType)) =>
            // Replace with loop variable reference
            new RefLocal(ref.location, ref.frame, loopVarIndex, paramType)
          case None =>
            // Not a parameter, keep as-is
            ref
        }

      case call: Call =>
        val rewrittenTarget = rewriteTermRefs(call.target, loopVarMapping)
        val rewrittenParams = call.parameters.map(p => rewriteTermRefs(p, loopVarMapping))
        new Call(call.location, rewrittenTarget, call.method, rewrittenParams)

      case call: CallStatic =>
        val rewrittenParams = call.parameters.map(p => rewriteTermRefs(p, loopVarMapping))
        new CallStatic(call.location, call.target, call.method, rewrittenParams)

      case binTerm: BinaryTerm =>
        val rewrittenLhs = rewriteTermRefs(binTerm.lhs, loopVarMapping)
        val rewrittenRhs = rewriteTermRefs(binTerm.rhs, loopVarMapping)
        new BinaryTerm(binTerm.location, binTerm.kind, binTerm.`type`, rewrittenLhs, rewrittenRhs)

      case unTerm: UnaryTerm =>
        val rewrittenOperand = rewriteTermRefs(unTerm.operand, loopVarMapping)
        new UnaryTerm(unTerm.location, unTerm.kind, unTerm.`type`, rewrittenOperand)

      case setLocal: SetLocal =>
        val rewrittenValue = rewriteTermRefs(setLocal.value, loopVarMapping)
        // Check if we're setting a parameter - if so, redirect to loop variable
        loopVarMapping.find(_._1 == setLocal.index) match {
          case Some((_, loopVarIndex, _)) =>
            new SetLocal(setLocal.location, setLocal.frame, loopVarIndex, setLocal.`type`, rewrittenValue)
          case None =>
            new SetLocal(setLocal.location, setLocal.frame, setLocal.index, setLocal.`type`, rewrittenValue)
        }

      case _ =>
        // For literals and other terms, return as-is
        term
    }
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
      case ret: Return =>
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
    paramTemps: IndexedSeq[(Int, Int, Type)]
  ): Seq[ActionStatement] = {
    stmts.map { stmt =>
      transformStatement(stmt, method, paramTemps)
    }
  }

  /**
   * Transforms a single statement, replacing tail calls.
   */
  private def transformStatement(
    stmt: ActionStatement,
    method: MethodDefinition,
    paramTemps: IndexedSeq[(Int, Int, Type)]
  ): ActionStatement = {
    stmt match {
      case ret: Return if ret.term != null && isSelfCall(ret.term, method) =>
        // Tail call found - replace with parameter updates
        transformTailCall(ret.term, paramTemps)

      case block: StatementBlock =>
        // Recursively transform statements in block
        val transformed = transformStatements(block.statements.toSeq, method, paramTemps)
        new StatementBlock(block.location, transformed: _*)

      case ifStmt: IfStatement =>
        // Transform both branches
        val transformedThen = transformStatement(ifStmt.thenStatement, method, paramTemps)
        val transformedElse = if (ifStmt.elseStatement != null) {
          transformStatement(ifStmt.elseStatement, method, paramTemps)
        } else null

        new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)

      case _ =>
        // Keep as-is
        stmt
    }
  }

  /**
   * Transforms a tail call into loop variable assignments.
   *
   * For `return method(arg1, arg2, ...)`:
   * 1. temp1 = arg1; temp2 = arg2; ... (evaluate all new values first)
   * 2. loopVar1 = temp1; loopVar2 = temp2; ... (update loop variables)
   * 3. (continue - loop restarts)
   *
   * We use temporary variables to ensure correct evaluation order.
   * Example: factorial(n-1, n*acc) must evaluate both args with old n value.
   */
  private def transformTailCall(
    term: Term,
    loopVarMapping: IndexedSeq[(Int, Int, Type)]
  ): ActionStatement = {
    val newArgs: Array[Term] = term match {
      case call: Call => call.parameters
      case call: CallStatic => call.parameters
      case _ => Array.empty
    }

    val assignments = mutable.ArrayBuffer[ActionStatement]()

    // Temporary variables come after loop variables
    // If we have loop vars at indices 2, 3, then temps are at 4, 5
    val maxLoopVarIndex = if (loopVarMapping.isEmpty) 0 else loopVarMapping.map(_._2).max
    val tempOffset = maxLoopVarIndex + 1

    // Step 1: Assign new argument values to temporary variables
    for (i <- newArgs.indices) {
      val (_, _, paramType) = loopVarMapping(i)
      val newValue = newArgs(i)
      val tempIndex = tempOffset + i

      // temp[i] = newArgs[i]
      val tempAssignment = new SetLocal(null, 0, tempIndex, paramType, newValue)
      assignments += new ExpressionActionStatement(null, tempAssignment)
    }

    // Step 2: Assign temporary variables to loop variables
    for (i <- loopVarMapping.indices) {
      val (_, loopVarIndex, paramType) = loopVarMapping(i)
      val tempIndex = tempOffset + i

      // loopVar[i] = temp[i]
      val tempRef = new RefLocal(null, 0, tempIndex, paramType)
      val loopVarAssignment = new SetLocal(null, 0, loopVarIndex, paramType, tempRef)
      assignments += new ExpressionActionStatement(null, loopVarAssignment)
    }

    // Return a block with all assignments
    // After this, the loop will continue automatically
    new StatementBlock(null, assignments.toSeq: _*)
  }
}
