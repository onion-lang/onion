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
    if (!isPrivate(method)) {
      if (config.verbose) {
        System.err.println(s"[TCO] Skipping non-private method: ${method.classType.name}.${method.name}")
      }
      return false
    }

    // Skip methods with @TailRecursive annotation (they may be part of mutual recursion)
    if (method.annotations.contains("TailRecursive")) {
      if (config.verbose) {
        System.err.println(s"[TCO] Skipping @TailRecursive annotated method: ${method.classType.name}.${method.name}")
      }
      return false
    }

    val hasTailCall = containsTailCall(block.statements.toSeq, method)
    if (config.verbose) {
      System.err.println(s"[TCO] Method ${method.classType.name}.${method.name}: hasTailCall=$hasTailCall")
    }
    hasTailCall
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
    if (config.verbose) {
      System.err.println(s"[TCO]   checkStatementForTailCall: ${stmt.getClass.getSimpleName}")
    }
    stmt match {
      case ret: Return =>
        val result = ret.term != null && isSelfCall(ret.term, method)
        if (config.verbose) {
          System.err.println(s"[TCO]     Return statement: hasSelfCall=$result")
        }
        result
      case block: StatementBlock =>
        containsTailCall(block.statements.toSeq, method)
      case ifStmt: IfStatement =>
        if (config.verbose) {
          System.err.println(s"[TCO]     IfStatement: checking then and else branches")
        }
        val thenResult = checkStatementForTailCall(ifStmt.thenStatement, method)
        val elseResult = if (ifStmt.elseStatement != null) checkStatementForTailCall(ifStmt.elseStatement, method) else false
        if (config.verbose) {
          System.err.println(s"[TCO]     IfStatement: then=$thenResult, else=$elseResult")
        }
        thenResult || elseResult
      case exprStmt: ExpressionActionStatement =>
        // Check expression in tail position
        if (config.verbose) {
          System.err.println(s"[TCO]     ExpressionActionStatement: checking term")
        }
        val result = isSelfCall(exprStmt.term, method)
        if (config.verbose) {
          System.err.println(s"[TCO]     ExpressionActionStatement: hasSelfCall=$result")
        }
        result
      case _ =>
        if (config.verbose) {
          System.err.println(s"[TCO]     Unknown statement type")
        }
        false
    }
  }

  private def isSelfCall(term: Term, method: MethodDefinition): Boolean = {
    if (config.verbose) {
      System.err.println(s"[TCO]   Checking term type: ${term.getClass.getSimpleName}")
    }
    term match {
      case call: Call =>
        call.method match {
          case targetMethod: MethodDefinition =>
            val isSelf = targetMethod.name == method.name &&
            targetMethod.classType.name == method.classType.name &&
            argumentTypesMatch(targetMethod.arguments, method.arguments)
            if (config.verbose) {
              System.err.println(s"[TCO]   Call to ${targetMethod.name}: isSelf=$isSelf")
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
              System.err.println(s"[TCO]   CallStatic to ${targetMethod.name}: isSelf=$isSelf")
            }
            isSelf
          case _ => false
        }
      case stmtTerm: StatementTerm =>
        // Check if-else expression (wrapped in StatementTerm)
        if (config.verbose) {
          System.err.println(s"[TCO]   StatementTerm wrapping: ${stmtTerm.statement.getClass.getSimpleName}")
        }
        checkStatementForTailCall(stmtTerm.statement, method)
      case begin: Begin =>
        // Check all terms in begin block for if-else expressions
        if (begin.terms.nonEmpty) {
          if (config.verbose) {
            System.err.println(s"[TCO]   Begin with ${begin.terms.length} terms:")
            begin.terms.zipWithIndex.foreach { case (t, i) =>
              System.err.println(s"[TCO]     Term $i: ${t.getClass.getSimpleName}")
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
          System.err.println(s"[TCO]   SetLocal: checking value")
        }
        val result = isSelfCall(setLocal.value, method)
        if (config.verbose) {
          System.err.println(s"[TCO]   SetLocal: value has self call=$result")
        }
        result
      case _ =>
        if (config.verbose) {
          System.err.println(s"[TCO]   Unknown term type, not a self call")
        }
        false
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

    // For multiple parameters, create temp variables to preserve evaluation order
    // Temp variables come after loop variables
    val tempVarMapping = if (paramCount >= 2) {
      val tempVarOffset = loopVarOffset + paramCount  // After loop variables
      Some((0 until paramCount).map { i =>
        val tempVarIndex = tempVarOffset + i
        val paramType = method.arguments(i)
        (i, tempVarIndex, paramType)
      }.toIndexedSeq)
    } else {
      None
    }

    // Register loop variables + temp variables in MethodDefinition for bytecode generation
    val allVarsForSlotAllocation = loopVarMapping.map { case (_, loopVarIndex, paramType) =>
      (loopVarIndex, paramType)
    } ++ tempVarMapping.getOrElse(Seq.empty).map { case (_, tempVarIndex, paramType) =>
      (tempVarIndex, paramType)
    }

    method.setTcoLoopVars(allVarsForSlotAllocation)

    System.err.println(s"[TCO] Registered ${loopVarMapping.length} loop variables for method ${method.name}")
    if (tempVarMapping.isDefined) {
      System.err.println(s"[TCO] Registered ${tempVarMapping.get.length} temp variables for method ${method.name}")
    }

    // Step 1: Create initialization statements (copy parameters to loop variables)
    val initStatements = loopVarMapping.map { case (paramIndex, loopVarIndex, paramType) =>
      val paramRef = new RefLocal(null, 0, paramIndex, paramType)
      val loopVarAssignment = new SetLocal(null, 0, loopVarIndex, paramType, paramRef)
      new ExpressionActionStatement(null, loopVarAssignment)
    }

    // Step 2: Rewrite all parameter references in the method body to use loop variables
    System.err.println(s"[TCO optimizeMethod] Step 2: Rewriting parameter references, original body has ${block.statements.length} statements")
    val rewrittenBody = rewriteParameterReferences(
      block.statements.toSeq,
      loopVarMapping
    )
    System.err.println(s"[TCO optimizeMethod] Step 2 complete: rewrittenBody has ${rewrittenBody.length} statements")
    rewrittenBody.zipWithIndex.foreach { case (stmt, i) =>
      System.err.println(s"[TCO optimizeMethod]   rewrittenBody[$i]: ${stmt.getClass.getSimpleName}")
      stmt match {
        case block: StatementBlock =>
          System.err.println(s"[TCO optimizeMethod]     StatementBlock with ${block.statements.length} statements:")
          block.statements.zipWithIndex.foreach { case (s, j) =>
            System.err.println(s"[TCO optimizeMethod]       stmt[$j]: ${s.getClass.getSimpleName}")
          }
        case ret: Return =>
          val hasTailCall = if (ret.term != null) isSelfCall(ret.term, method) else false
          System.err.println(s"[TCO optimizeMethod]     Return: term=${if (ret.term != null) ret.term.getClass.getSimpleName else "null"}, hasTailCall=$hasTailCall")
        case _ =>
      }
    }

    // Step 3: Transform tail calls to loop variable updates
    System.err.println(s"[TCO optimizeMethod] Step 3: Transforming tail calls")
    val transformedBody = transformStatements(
      rewrittenBody,
      method,
      loopVarMapping
    )
    System.err.println(s"[TCO optimizeMethod] Step 3 complete: transformedBody has ${transformedBody.length} statements")
    transformedBody.zipWithIndex.foreach { case (stmt, i) =>
      System.err.println(s"[TCO optimizeMethod]   transformedBody[$i]: ${stmt.getClass.getSimpleName}")
      stmt match {
        case block: StatementBlock =>
          System.err.println(s"[TCO optimizeMethod]     StatementBlock with ${block.statements.length} statements:")
          block.statements.zipWithIndex.foreach { case (s, j) =>
            System.err.println(s"[TCO optimizeMethod]       stmt[$j]: ${s.getClass.getSimpleName}")
          }
        case _ =>
      }
    }

    // Remove unreachable return statements at the end
    System.err.println(s"[TCO optimizeMethod] Step 4: Removing trailing unreachable returns")
    val cleanedBody = removeTrailingUnreachableReturns(transformedBody)
    System.err.println(s"[TCO optimizeMethod] Step 4 complete: cleanedBody has ${cleanedBody.length} statements")
    cleanedBody.zipWithIndex.foreach { case (stmt, i) =>
      System.err.println(s"[TCO optimizeMethod]   cleanedBody[$i]: ${stmt.getClass.getSimpleName}")
    }

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
        System.err.println(s"[TCO rewriteStatementRefs] Rewriting IfStatement condition")
        val rewrittenCond = rewriteTermRefs(ifStmt.condition, loopVarMapping)
        System.err.println(s"[TCO rewriteStatementRefs] IfStatement condition rewritten")
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
            System.err.println(s"[TCO rewriteTermRefs] RefLocal: index=${ref.index} → loopVarIndex=$loopVarIndex")
            // Replace with loop variable reference
            new RefLocal(ref.location, ref.frame, loopVarIndex, paramType)
          case None =>
            System.err.println(s"[TCO rewriteTermRefs] RefLocal: index=${ref.index}, not a parameter")
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

      case begin: Begin =>
        // Recursively rewrite all terms in the Begin block
        System.err.println(s"[TCO rewriteTermRefs] Begin with ${begin.terms.length} terms")
        begin.terms.zipWithIndex.foreach { case (t, i) =>
          System.err.println(s"[TCO rewriteTermRefs]   Begin term[$i]: ${t.getClass.getSimpleName}")
        }
        val rewrittenTerms = begin.terms.map(t => rewriteTermRefs(t, loopVarMapping))
        new Begin(begin.location, rewrittenTerms)

      case stmtTerm: StatementTerm =>
        // Rewrite the statement within StatementTerm
        System.err.println(s"[TCO rewriteTermRefs] StatementTerm with statement: ${stmtTerm.statement.getClass.getSimpleName}")
        val rewrittenStmt = rewriteStatementRefs(stmtTerm.statement, loopVarMapping)
        new StatementTerm(stmtTerm.location, rewrittenStmt, stmtTerm.termType)

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
        System.err.println(s"[TCO transformStatement] Return with tail call, term type=${ret.term.getClass.getSimpleName}")
        // Tail call found
        // Check if it's wrapped in Begin with IfStatement
        ret.term match {
          case begin: Begin if begin.terms.nonEmpty =>
            System.err.println(s"[TCO transformStatement] Return: Begin with ${begin.terms.length} terms")
            // Look for IfStatement in Begin
            val ifStmtOpt = begin.terms.collectFirst {
              case stmtTerm: StatementTerm =>
                stmtTerm.statement match {
                  case ifStmt: IfStatement => Some(ifStmt)
                  case _ => None
                }
            }.flatten

            System.err.println(s"[TCO transformStatement] Return: ifStmtOpt = ${ifStmtOpt.isDefined}")
            ifStmtOpt match {
              case Some(ifStmt) =>
                System.err.println(s"[TCO transformStatement] Return: Transforming IfStatement branches")

                // Get the result variable from the last term of Begin (RefLocal)
                val resultTerm = begin.terms.last match {
                  case refLocal: RefLocal => refLocal
                  case other =>
                    System.err.println(s"[TCO transformStatement] Warning: Begin's last term is not RefLocal but ${other.getClass.getSimpleName}")
                    other
                }

                // Transform the branches
                // For branches without tail call, we need to add Return(resultTerm)
                // For branches with tail call, transformStatement will add Continue

                // Helper to add Return if branch doesn't end with Continue
                def ensureProperBranchEnd(transformedBranch: ActionStatement, branchName: String): ActionStatement = {
                  System.err.println(s"[TCO ensureProperBranchEnd] $branchName: input type=${transformedBranch.getClass.getSimpleName}")
                  val result = transformedBranch match {
                    case block: StatementBlock if block.statements.nonEmpty =>
                      System.err.println(s"[TCO ensureProperBranchEnd] $branchName: StatementBlock with ${block.statements.length} statements")
                      block.statements.zipWithIndex.foreach { case (stmt, i) =>
                        System.err.println(s"[TCO ensureProperBranchEnd]   stmt[$i]: ${stmt.getClass.getSimpleName}")
                      }
                      // Check if last statement is Continue
                      block.statements.last match {
                        case _: Continue =>
                          System.err.println(s"[TCO ensureProperBranchEnd] $branchName: Already has Continue")
                          // Already has Continue, keep as-is
                          block
                        case _ =>
                          System.err.println(s"[TCO ensureProperBranchEnd] $branchName: Adding Return")
                          // No Continue, add Return
                          val stmtsWithReturn = block.statements.toSeq :+ new Return(null, resultTerm)
                          new StatementBlock(block.location, stmtsWithReturn: _*)
                      }
                    case other =>
                      System.err.println(s"[TCO ensureProperBranchEnd] $branchName: Single statement, wrapping with Return")
                      // Single statement (ExpressionActionStatement, etc.)
                      // Wrap in block with Return
                      new StatementBlock(null, Seq(other, new Return(null, resultTerm)): _*)
                  }
                  System.err.println(s"[TCO ensureProperBranchEnd] $branchName: result type=${result.getClass.getSimpleName}")
                  result
                }

                val transformedThen = ensureProperBranchEnd(transformStatement(ifStmt.thenStatement, method, paramTemps), "then")
                val transformedElse = if (ifStmt.elseStatement != null) {
                  ensureProperBranchEnd(transformStatement(ifStmt.elseStatement, method, paramTemps), "else")
                } else null

                // Return the transformed IfStatement directly
                new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)
              case None =>
                System.err.println(s"[TCO transformStatement] Return: No IfStatement found, calling transformTailCall")
                // No IfStatement found, transform as direct call
                transformTailCall(ret.term, paramTemps)
            }
          case stmtTerm: StatementTerm =>
            // Explicit return: Return(StatementTerm(IfStatement))
            System.err.println(s"[TCO transformStatement] Return: StatementTerm wrapping ${stmtTerm.statement.getClass.getSimpleName}")
            stmtTerm.statement match {
              case ifStmt: IfStatement =>
                System.err.println(s"[TCO transformStatement] Return: Transforming explicit return IfStatement")
                // IfStatementの両ブランチをtransformStatementで処理
                val transformedThen = transformStatement(ifStmt.thenStatement, method, paramTemps)
                val transformedElse = if (ifStmt.elseStatement != null) {
                  transformStatement(ifStmt.elseStatement, method, paramTemps)
                } else null
                new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)
              case _ =>
                // Other StatementTerms: fallback to transformTailCall
                transformTailCall(ret.term, paramTemps)
            }

          case _ =>
            System.err.println(s"[TCO transformStatement] Return: Not Begin, calling transformTailCall")
            // Direct tail call (not in Begin)
            transformTailCall(ret.term, paramTemps)
        }

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

      case exprStmt: ExpressionActionStatement =>
        System.err.println(s"[TCO transformStatement] ExpressionActionStatement, term type=${exprStmt.term.getClass.getSimpleName}")
        // Check if the expression contains a tail call
        exprStmt.term match {
          case setLocal: SetLocal if isSelfCall(setLocal.value, method) =>
            System.err.println(s"[TCO transformStatement] SetLocal with tail call, value type=${setLocal.value.getClass.getSimpleName}")
            // Tail call is being assigned to a variable
            // Check if it's wrapped in Begin with IfStatement
            setLocal.value match {
              case begin: Begin if begin.terms.nonEmpty =>
                System.err.println(s"[TCO transformStatement] Begin with ${begin.terms.length} terms")
                // Look for IfStatement in Begin
                val ifStmtOpt = begin.terms.collectFirst {
                  case stmtTerm: StatementTerm =>
                    stmtTerm.statement match {
                      case ifStmt: IfStatement => Some(ifStmt)
                      case _ => None
                    }
                }.flatten

                System.err.println(s"[TCO transformStatement] ifStmtOpt = ${ifStmtOpt.isDefined}")
                ifStmtOpt match {
                  case Some(ifStmt) =>
                    System.err.println(s"[TCO transformStatement] Transforming IfStatement branches")
                    // Transform the IfStatement's branches
                    val transformedThen = transformStatement(ifStmt.thenStatement, method, paramTemps)
                    val transformedElse = if (ifStmt.elseStatement != null) {
                      transformStatement(ifStmt.elseStatement, method, paramTemps)
                    } else null
                    new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)
                  case None =>
                    System.err.println(s"[TCO transformStatement] No IfStatement found, calling transformTailCall")
                    // No IfStatement found, try to transform as direct call
                    transformTailCall(setLocal.value, paramTemps)
                }
              case _ =>
                System.err.println(s"[TCO transformStatement] Not Begin, calling transformTailCall")
                // Direct tail call (not in Begin)
                transformTailCall(setLocal.value, paramTemps)
            }
          case _ =>
            System.err.println(s"[TCO transformStatement] No tail call in ExpressionActionStatement")
            // No tail call, keep as-is
            exprStmt
        }

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
    // Extract the actual Call from wrapped structures (Begin, StatementTerm, etc.)
    def extractCall(t: Term, depth: Int = 0): Option[Term] = {
      val indent = "  " * depth
      System.err.println(s"$indent[TCO extractCall] depth=$depth, term type=${t.getClass.getSimpleName}")

      t match {
        case c: Call =>
          System.err.println(s"$indent[TCO extractCall] Found Call!")
          Some(c)
        case c: CallStatic =>
          System.err.println(s"$indent[TCO extractCall] Found CallStatic!")
          Some(c)
        case begin: Begin =>
          System.err.println(s"$indent[TCO extractCall] Begin with ${begin.terms.length} terms:")
          begin.terms.zipWithIndex.foreach { case (term, i) =>
            System.err.println(s"$indent  Term $i: ${term.getClass.getSimpleName}")
          }
          // If-else expression: recursively check all terms
          begin.terms.flatMap(t => extractCall(t, depth + 1)).headOption
        case stmtTerm: StatementTerm =>
          System.err.println(s"$indent[TCO extractCall] StatementTerm wrapping ${stmtTerm.statement.getClass.getSimpleName}")
          // Unwrap StatementTerm
          stmtTerm.statement match {
            case exprStmt: ExpressionActionStatement =>
              extractCall(exprStmt.term, depth + 1)
            case ifStmt: IfStatement =>
              System.err.println(s"$indent[TCO extractCall] IfStatement - should have been handled in transformStatement")
              None
            case _ => None
          }
        case setLocal: SetLocal =>
          System.err.println(s"$indent[TCO extractCall] SetLocal, checking value")
          // Check value being assigned
          extractCall(setLocal.value, depth + 1)
        case other =>
          System.err.println(s"$indent[TCO extractCall] Unhandled type: ${other.getClass.getSimpleName}")
          None
      }
    }

    val actualCall = extractCall(term).getOrElse {
      throw new RuntimeException(
        s"transformTailCall: Could not extract Call from term type=${term.getClass.getSimpleName}"
      )
    }

    val newArgs: Array[Term] = actualCall match {
      case call: Call => call.parameters
      case call: CallStatic => call.parameters
      case _ => Array.empty
    }

    if (config.verbose) {
      System.err.println(s"[TCO]   transformTailCall: term type=${term.getClass.getSimpleName}, actualCall type=${actualCall.getClass.getSimpleName}, newArgs.length=${newArgs.length}")
    }

    val assignments = mutable.ArrayBuffer[ActionStatement]()

    if (newArgs.length == 1) {
      // Single argument: can directly assign to loop variable (no temp needed)
      val (_, loopVarIndex, paramType) = loopVarMapping(0)
      val newValue = newArgs(0)

      // loopVar = newValue
      val loopVarAssignment = new SetLocal(null, 0, loopVarIndex, paramType, newValue)
      assignments += new ExpressionActionStatement(null, loopVarAssignment)
    } else if (newArgs.length > 1) {
      // Multiple arguments: use temporary variables to preserve evaluation order
      System.err.println(s"[TCO transformTailCall] Multiple arguments: ${newArgs.length}")

      // Temp variable indices come after loop variables
      // For factorial(n: Int, acc: Int):
      //   loopVarMapping(0) = (0, 2, Int)  // n -> n_loop
      //   loopVarMapping(1) = (1, 3, Int)  // acc -> acc_loop
      //   tempVarIndex(0) = 4  // temp0
      //   tempVarIndex(1) = 5  // temp1
      val tempVarOffset = loopVarMapping.length + loopVarMapping.length  // paramCount + paramCount

      // Step 1: Evaluate all arguments and assign to temp variables
      // This ensures correct evaluation order (all args use old parameter values)
      newArgs.zipWithIndex.foreach { case (arg, i) =>
        val (_, _, paramType) = loopVarMapping(i)
        val tempVarIndex = tempVarOffset + i
        System.err.println(s"[TCO transformTailCall]   Step 1: Assign temp[$i]: tempVarIndex=$tempVarIndex")
        val tempAssignment = new SetLocal(null, 0, tempVarIndex, paramType, arg)
        assignments += new ExpressionActionStatement(null, tempAssignment)
      }

      // Step 2: Copy temp variables to loop variables
      // This updates the loop variables atomically after all args are evaluated
      loopVarMapping.zipWithIndex.foreach { case ((_, loopVarIndex, paramType), i) =>
        val tempVarIndex = tempVarOffset + i
        val tempRef = new RefLocal(null, 0, tempVarIndex, paramType)
        System.err.println(s"[TCO transformTailCall]   Step 2: Copy temp[$i] -> loopVar[$i]: loopVarIndex=$loopVarIndex")
        val loopVarAssignment = new SetLocal(null, 0, loopVarIndex, paramType, tempRef)
        assignments += new ExpressionActionStatement(null, loopVarAssignment)
      }
    } else {
      throw new RuntimeException("transformTailCall: No arguments in tail call")
    }

    // Add continue statement to restart the loop
    assignments += new Continue(null)

    // Return a block with all assignments and continue
    new StatementBlock(null, assignments.toSeq: _*)
  }
}
