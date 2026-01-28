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
 * Mutual Recursion Optimization
 *
 * Converts mutually recursive functions marked with @TailRecursive into a state machine.
 *
 * == Strategy ==
 *
 * For methods annotated with @TailRecursive that call each other:
 * 1. Detect mutual recursion groups (strongly connected components)
 * 2. Transform group into single method with state machine
 * 3. Each original method becomes a state in switch statement
 * 4. Tail calls become state transitions
 *
 * Example:
 * {{{
 * @TailRecursive
 * def isEven(n: Int): Boolean {
 *   if (n == 0) return true
 *   return isOdd(n - 1)
 * }
 *
 * @TailRecursive
 * def isOdd(n: Int): Boolean {
 *   if (n == 0) return false
 *   return isEven(n - 1)
 * }
 *
 * // Transformed to:
 * def isEven_isOdd(n: Int, state: Int): Boolean {
 *   while (true) {
 *     switch (state) {
 *       case 0: // isEven
 *         if (n == 0) return true
 *         n = n - 1
 *         state = 1 // transition to isOdd
 *         continue
 *       case 1: // isOdd
 *         if (n == 0) return false
 *         n = n - 1
 *         state = 0 // transition to isEven
 *         continue
 *     }
 *   }
 * }
 * }}}
 */
class MutualRecursionOptimization(config: CompilerConfig)
  extends Processor[Seq[ClassDefinition], Seq[ClassDefinition]] {

  class OptimizationEnvironment
  type Environment = OptimizationEnvironment

  def newEnvironment(source: Seq[ClassDefinition]): Environment =
    new OptimizationEnvironment

  def processBody(
    source: Seq[ClassDefinition],
    environment: OptimizationEnvironment
  ): Seq[ClassDefinition] = {
    source.map { classDef =>
      optimizeClass(classDef)
    }
  }

  private def optimizeClass(classDef: ClassDefinition): ClassDefinition = {
    // Find all methods with @TailRecursive annotation
    val annotatedMethods = classDef.methods.collect {
      case method: MethodDefinition if hasAnnotation(method, "TailRecursive") =>
        method
    }

    if (annotatedMethods.isEmpty) {
      return classDef // No optimization needed
    }

    // Build call graph
    val callGraph = buildCallGraph(annotatedMethods)

    // Find strongly connected components (mutual recursion groups)
    val mutualGroups = findMutualRecursionGroups(annotatedMethods, callGraph)

    if (mutualGroups.isEmpty) {
      return classDef // No mutual recursion detected
    }

    // Transform each group
    mutualGroups.foreach { group =>
      if (group.size > 1) {
        if (config.verbose) {
          val methodNames = group.map(_.name).mkString(", ")
          System.err.println(s"[Mutual TCO] Detected mutual recursion group: $methodNames")
        }

        // Validate group can be optimized
        validateGroup(group) match {
          case Some(error) =>
            if (config.verbose) {
              System.err.println(s"[Mutual TCO] Cannot optimize: $error")
            }
          case None =>
            if (config.verbose) {
              System.err.println(s"[Mutual TCO] Optimization will be applied")
            }
            transformGroup(classDef, group)
        }
      }
    }

    classDef
  }

  /**
   * Check if method has specific annotation
   */
  private def hasAnnotation(method: MethodDefinition, annotationName: String): Boolean = {
    method.annotations.contains(annotationName)
  }

  /**
   * Build call graph using the TailCallGraphAnalysis helper.
   */
  private def buildCallGraph(methods: Seq[MethodDefinition]): Map[MethodDefinition, Set[MethodDefinition]] =
    TailCallGraphAnalysis.buildCallGraph(methods)

  /**
   * Find strongly connected components (mutual recursion groups).
   * Delegates to the generic SCC algorithm.
   */
  private def findMutualRecursionGroups(
    methods: Seq[MethodDefinition],
    callGraph: Map[MethodDefinition, Set[MethodDefinition]]
  ): Seq[Seq[MethodDefinition]] = {
    StronglyConnectedComponents.findSCCs(methods, m => callGraph.getOrElse(m, Set.empty))
  }

  /**
   * Validate that a group can be optimized
   */
  private def validateGroup(group: Seq[MethodDefinition]): Option[String] = {
    // Check 1: All methods must be private (to avoid breaking public API)
    val nonPrivateMethods = group.filterNot(isPrivate)
    if (nonPrivateMethods.nonEmpty) {
      val names = nonPrivateMethods.map(_.name).mkString(", ")
      return Some(s"All methods must be private for mutual recursion optimization. Non-private: $names")
    }

    // Check 2: All methods must have same return type
    val returnTypes = group.map(_.returnType.name).toSet
    if (returnTypes.size > 1) {
      return Some(s"Methods have different return types: ${returnTypes.mkString(", ")}")
    }

    // Check 3: All methods must be in same class (already guaranteed by our collection)

    // Check 4: All tail calls must be to methods in the group
    val groupNames = group.map(_.name).toSet
    group.foreach { method =>
      val tailCalls = TailCallGraphAnalysis.findTailCalls(method)
      val externalCalls = tailCalls.filterNot(groupNames.contains)
      if (externalCalls.nonEmpty) {
        return Some(s"Method ${method.name} has tail calls to non-group methods: ${externalCalls.mkString(", ")}")
      }
    }

    None // All validations passed
  }

  /**
   * Check if method is private
   */
  private def isPrivate(method: MethodDefinition): Boolean = {
    // Use Onion's M_PRIVATE constant, not Java's Modifier.PRIVATE
    (method.modifier & AST.M_PRIVATE) != 0
  }

  /**
   * Transform a mutual recursion group into a state machine
   */
  private def transformGroup(classDef: ClassDefinition, group: Seq[MethodDefinition]): Unit = {
    // Assign state IDs to each method
    val stateIds = group.zipWithIndex.toMap

    if (config.verbose) {
      group.zipWithIndex.foreach { case (method, stateId) =>
        System.err.println(s"[Mutual TCO]   State $stateId: ${method.name}")
      }
    }

    // Step 1: Check parameter compatibility
    val paramCounts = group.map(_.arguments.length).toSet
    if (paramCounts.size > 1) {
      if (config.verbose) {
        System.err.println(s"[Mutual TCO] ERROR: Methods have different parameter counts: $paramCounts")
      }
      return
    }

    val paramCount = group.head.arguments.length

    // Check parameter types are compatible across all methods
    for (paramIdx <- 0 until paramCount) {
      val paramTypesSet = group.map(_.arguments(paramIdx).name).toSet
      if (paramTypesSet.size > 1) {
        if (config.verbose) {
          System.err.println(s"[Mutual TCO] ERROR: Parameter $paramIdx has different types: $paramTypesSet")
        }
        return
      }
    }

    // Step 2: Create state machine method name and structure
    val stateMachineMethodName = group.map(_.name).mkString("_")
    val returnType = group.head.returnType
    val paramTypes = group.head.arguments

    if (config.verbose) {
      System.err.println(s"[Mutual TCO] Creating state machine method: $stateMachineMethodName")
      System.err.println(s"[Mutual TCO]   Parameters: ${paramTypes.map(_.name).mkString(", ")}")
      System.err.println(s"[Mutual TCO]   Return type: ${returnType.name}")
    }

    // Step 3: Generate state machine method
    val stateMachineMethod = createStateMachineMethod(
      classDef,
      stateMachineMethodName,
      group,
      stateIds,
      paramTypes,
      returnType
    )

    // Step 4: Add state machine method to class
    classDef.methods_.add(stateMachineMethod)
    if (config.verbose) {
      System.err.println(s"[Mutual TCO] Added state machine method to class")
    }

    // Step 5: Replace original method bodies with forwarders
    group.foreach { method =>
      val stateId = stateIds(method)
      val forwarderBody = createForwarderBody(
        method,
        stateMachineMethod,
        stateId,
        paramCount
      )
      method.setBlock(forwarderBody)
      if (config.verbose) {
        System.err.println(s"[Mutual TCO] Replaced ${method.name} with forwarder (state $stateId)")
      }
    }

    if (config.verbose) {
      System.err.println(s"[Mutual TCO] State machine optimization complete")
    }
  }

  /**
   * Create the state machine method
   */
  private def createStateMachineMethod(
    classDef: ClassDefinition,
    methodName: String,
    group: Seq[MethodDefinition],
    stateIds: Map[MethodDefinition, Int],
    paramTypes: Array[Type],
    returnType: Type
  ): MethodDefinition = {
    val loc: Location = null

    // State machine method parameters: original params + state parameter
    val stateType = TypedAST.BasicType.INT
    val allParams = paramTypes :+ stateType

    // Create a private static method
    val modifier = AST.M_PRIVATE | AST.M_STATIC

    // Generate state machine body
    val body = createStateMachineBody(
      group,
      stateIds,
      paramTypes.length,
      returnType
    )

    val stateMachineMethod = new MethodDefinition(
      location = loc,
      modifier = modifier,
      classType = classDef,
      name = methodName,
      arguments = allParams,
      returnType = returnType,
      block = body,
      typeParameters = Array(),
      throwsTypes = Array(),
      vararg = false,
      annotations = scala.collection.immutable.Set.empty
    )

    if (config.verbose) {
      System.err.println(s"[Mutual TCO] Generated state machine method: $methodName")
    }
    stateMachineMethod
  }

  /**
   * Create the state machine body: while(true) + switch
   */
  private def createStateMachineBody(
    group: Seq[MethodDefinition],
    stateIds: Map[MethodDefinition, Int],
    paramCount: Int,
    returnType: Type
  ): StatementBlock = {
    val loc: Location = null

    // Local variable indices:
    // 0..(paramCount-1): parameters
    // paramCount: state parameter
    // (paramCount+1)..(paramCount+paramCount): loop variables for parameters
    // (paramCount+paramCount+1): loop variable for state
    val stateParamIndex = paramCount
    val loopVarOffset = paramCount + 1
    val stateLoopVarIndex = paramCount + paramCount + 1

    // Step 1: Initialize loop variables
    val initStatements = mutable.ArrayBuffer[ActionStatement]()

    // Copy parameters to loop variables
    for (i <- 0 until paramCount) {
      val paramRef = new RefLocal(loc, 0, i, group.head.arguments(i))
      val loopVarIndex = loopVarOffset + i
      val assignment = new SetLocal(loc, 0, loopVarIndex, group.head.arguments(i), paramRef)
      initStatements += new ExpressionActionStatement(loc, assignment)
    }

    // Copy state parameter to state loop variable
    val stateParamRef = new RefLocal(loc, 0, stateParamIndex, TypedAST.BasicType.INT)
    val stateLoopVarAssignment = new SetLocal(loc, 0, stateLoopVarIndex, TypedAST.BasicType.INT, stateParamRef)
    initStatements += new ExpressionActionStatement(loc, stateLoopVarAssignment)

    // Step 2: Create if-else chain for state dispatch
    val stateDispatch = createStateDispatch(
      group,
      stateIds,
      loopVarOffset,
      stateLoopVarIndex,
      paramCount,
      returnType
    )

    // Step 3: Create infinite loop
    val trueValue = new BoolValue(loc, true)
    val loop = new ConditionalLoop(loc, trueValue, stateDispatch)

    // Combine: init statements + loop
    val allStatements = initStatements.toSeq :+ loop
    new StatementBlock(loc, allStatements: _*)
  }

  /**
   * Create a forwarder body that calls the state machine method
   */
  private def createForwarderBody(
    originalMethod: MethodDefinition,
    stateMachineMethod: MethodDefinition,
    stateId: Int,
    paramCount: Int
  ): StatementBlock = {
    val loc: Location = null

    // Create parameter references
    val paramRefs = (0 until paramCount).map { i =>
      new RefLocal(loc, 0, i, originalMethod.arguments(i))
    }.toArray[Term]

    // Create state ID constant
    val stateIdValue = new IntValue(loc, stateId)

    // Create static call to state machine method
    val callParams = paramRefs :+ stateIdValue
    val stateMachineCall = new CallStatic(
      loc,
      originalMethod.classType,
      stateMachineMethod,
      callParams
    )

    // Return the result
    val returnStmt = new Return(loc, stateMachineCall)
    new StatementBlock(loc, returnStmt)
  }

  /**
   * Create state dispatch if-else chain
   */
  private def createStateDispatch(
    group: Seq[MethodDefinition],
    stateIds: Map[MethodDefinition, Int],
    loopVarOffset: Int,
    stateLoopVarIndex: Int,
    paramCount: Int,
    returnType: Type
  ): StatementBlock = {
    val loc: Location = null

    // Build if-else chain: if (state == 0) {...} else if (state == 1) {...} else {...}
    var currentElse: ActionStatement = createDefaultReturn(returnType)

    if (config.verbose) {
      System.err.println(s"[Mutual TCO]   Building if-else chain for ${group.size} states")
    }

    // Process methods in reverse order to build the chain from bottom up
    group.toSeq.reverse.foreach { method =>
      val stateId = stateIds(method)
      val stateIdValue = new IntValue(loc, stateId)

      if (config.verbose) {
        System.err.println(s"[Mutual TCO]   Processing state $stateId (${method.name})")
      }

      // Condition: state_loop == stateId
      // Create a fresh RefLocal for each condition
      val freshStateLoopVarRef = new RefLocal(loc, 0, stateLoopVarIndex, TypedAST.BasicType.INT)
      val condition = new BinaryTerm(
        loc,
        BinaryTerm.Kind.EQUAL,
        TypedAST.BasicType.BOOLEAN,
        freshStateLoopVarRef,
        stateIdValue
      )

      // Transform method body for state machine
      val transformedBody = transformMethodBodyForStateMachine(
        method.getBlock,
        stateIds,
        loopVarOffset,
        stateLoopVarIndex,
        paramCount
      )

      if (config.verbose) {
        System.err.println(s"[Mutual TCO]     Transformed body has ${transformedBody.statements.length} statements")
      }

      // Create if statement
      val ifStmt = new IfStatement(loc, condition, transformedBody, currentElse)
      currentElse = ifStmt
    }

    if (config.verbose) {
      System.err.println(s"[Mutual TCO]   If-else chain complete")
    }
    new StatementBlock(loc, currentElse)
  }

  /**
   * Transform a method body to use state transitions for tail calls
   */
  private def transformMethodBodyForStateMachine(
    body: StatementBlock,
    stateIds: Map[MethodDefinition, Int],
    loopVarOffset: Int,
    stateLoopVarIndex: Int,
    paramCount: Int
  ): StatementBlock = {
    val loc: Location = null
    val stateIdByName = stateIds.map { case (method, id) => method.name -> id }

    def transformStatement(stmt: ActionStatement): ActionStatement = {
      stmt match {
        case ret: Return if ret.term != null =>
          // Check if this is a tail call to another method in the group
          ret.term match {
            case call: Call =>
              call.method match {
                case targetMethod: MethodDefinition if stateIdByName.contains(targetMethod.name) =>
                  // Transform to: update params, set state, continue
                  if (config.verbose) {
                    System.err.println(s"[Mutual TCO]       Found tail call: ${targetMethod.name} -> state ${stateIdByName(targetMethod.name)}")
                  }
                  transformTailCallToStateTransition(
                    call.parameters,
                    stateIdByName(targetMethod.name),
                    loopVarOffset,
                    stateLoopVarIndex,
                    call.parameters.map(_.`type`)
                  )
                case _ =>
                  ret
              }
            case call: CallStatic =>
              call.method match {
                case targetMethod: MethodDefinition if stateIdByName.contains(targetMethod.name) =>
                  if (config.verbose) {
                    System.err.println(s"[Mutual TCO]       Found static tail call: ${targetMethod.name} -> state ${stateIdByName(targetMethod.name)}")
                  }
                  transformTailCallToStateTransition(
                    call.parameters,
                    stateIdByName(targetMethod.name),
                    loopVarOffset,
                    stateLoopVarIndex,
                    call.parameters.map(_.`type`)
                  )
                case _ =>
                  ret
              }
            case _ =>
              ret
          }

        case block: StatementBlock =>
          // Transform statements, and move tail calls into if-else structure
          val transformed = transformStatementsInBlock(block.statements.toSeq)
          new StatementBlock(block.location, transformed: _*)

        case ifStmt: IfStatement =>
          val transformedThen = transformStatement(ifStmt.thenStatement)
          val transformedElse = if (ifStmt.elseStatement != null) {
            transformStatement(ifStmt.elseStatement)
          } else null
          new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)

        case _ =>
          stmt
      }
    }

    // Transform statements in a block, merging tail calls into preceding if statements
    def transformStatementsInBlock(stmts: Seq[ActionStatement]): Seq[ActionStatement] = {
      if (config.verbose) {
        System.err.println(s"[Mutual TCO]         transformStatementsInBlock: ${stmts.length} statements")
      }

      if (stmts.length < 2) {
        stmts.map(transformStatement)
      } else {
        val lastIdx = stmts.length - 1
        val last = stmts(lastIdx)

        // Check if last statement is a tail call return
        last match {
          case ret: Return if ret.term != null && isTailCallInGroup(ret.term) =>
            if (config.verbose) {
              System.err.println(s"[Mutual TCO]         Last statement is tail call return!")
            }
            // Check if second-to-last is an if statement with return in then branch
            if (lastIdx > 0) {
              stmts(lastIdx - 1) match {
                case ifStmt: IfStatement if ifStmt.elseStatement == null =>
                  if (config.verbose) {
                    System.err.println(s"[Mutual TCO]         Merging tail call into else branch of if statement")
                  }
                  // Merge tail call into else branch
                  val tailCallTransform = transformStatement(ret)
                  val newIfStmt = new IfStatement(
                    ifStmt.location,
                    ifStmt.condition,
                    transformStatement(ifStmt.thenStatement),
                    tailCallTransform
                  )
                  stmts.take(lastIdx - 1).map(transformStatement) :+ newIfStmt
                case other =>
                  if (config.verbose) {
                    System.err.println(s"[Mutual TCO]         Second-to-last is not suitable if statement: ${other.getClass.getSimpleName}")
                  }
                  stmts.map(transformStatement)
              }
            } else {
              stmts.map(transformStatement)
            }
          case _ =>
            if (config.verbose) {
              System.err.println(s"[Mutual TCO]         Last statement is not tail call: ${last.getClass.getSimpleName}")
            }
            stmts.map(transformStatement)
        }
      }
    }

    def isTailCallInGroup(term: Term): Boolean = {
      val result = term match {
        case call: Call =>
          call.method match {
            case targetMethod: MethodDefinition =>
              val inGroup = stateIdByName.contains(targetMethod.name)
              if (config.verbose) {
                System.err.println(s"[Mutual TCO]         Checking Call: ${targetMethod.name}, in group: $inGroup")
              }
              inGroup
            case _ => false
          }
        case call: CallStatic =>
          call.method match {
            case targetMethod: MethodDefinition =>
              val inGroup = stateIdByName.contains(targetMethod.name)
              if (config.verbose) {
                System.err.println(s"[Mutual TCO]         Checking CallStatic: ${targetMethod.name}, in group: $inGroup")
              }
              inGroup
            case _ => false
          }
        case _ => false
      }
      result
    }

    // Rewrite parameter references to use loop variables
    val rewrittenBody = rewriteParameterReferences(body, loopVarOffset, paramCount)

    // Transform statements, merging tail calls into if-else structure
    val transformedStatements = transformStatementsInBlock(rewrittenBody.statements.toSeq)
    new StatementBlock(loc, transformedStatements: _*)
  }

  /**
   * Transform tail call to state transition
   */
  private def transformTailCallToStateTransition(
    newArgs: Array[Term],
    targetStateId: Int,
    loopVarOffset: Int,
    stateLoopVarIndex: Int,
    paramTypes: Array[Type]
  ): ActionStatement = {
    val loc: Location = null
    val assignments = mutable.ArrayBuffer[ActionStatement]()

    // Temporary variables come after state loop variable
    val tempOffset = stateLoopVarIndex + 1

    // Step 1: Assign new argument values to temporary variables
    for (i <- newArgs.indices) {
      val newValue = newArgs(i)
      val tempIndex = tempOffset + i
      val paramType = paramTypes(i)

      val tempAssignment = new SetLocal(loc, 0, tempIndex, paramType, newValue)
      assignments += new ExpressionActionStatement(loc, tempAssignment)
    }

    // Step 2: Assign temporary variables to loop variables
    for (i <- newArgs.indices) {
      val loopVarIndex = loopVarOffset + i
      val tempIndex = tempOffset + i
      val paramType = paramTypes(i)

      val tempRef = new RefLocal(loc, 0, tempIndex, paramType)
      val loopVarAssignment = new SetLocal(loc, 0, loopVarIndex, paramType, tempRef)
      assignments += new ExpressionActionStatement(loc, loopVarAssignment)
    }

    // Step 3: Set state loop variable
    val stateValue = new IntValue(loc, targetStateId)
    val stateAssignment = new SetLocal(loc, 0, stateLoopVarIndex, TypedAST.BasicType.INT, stateValue)
    assignments += new ExpressionActionStatement(loc, stateAssignment)

    // Step 4: Add Continue to jump back to loop start
    assignments += new Continue(loc)

    // Return a block with all assignments + continue
    new StatementBlock(loc, assignments.toSeq: _*)
  }

  /**
   * Rewrite parameter references to use loop variables
   */
  private def rewriteParameterReferences(
    body: StatementBlock,
    loopVarOffset: Int,
    paramCount: Int
  ): StatementBlock = {
    val loc: Location = null

    def rewriteTerm(term: Term): Term = {
      term match {
        case ref: RefLocal if ref.frame == 0 && ref.index < paramCount =>
          // This references a parameter (NOT state parameter), redirect to loop variable
          val newIndex = loopVarOffset + ref.index
          new RefLocal(loc, 0, newIndex, ref.`type`)

        case call: Call =>
          val rewrittenTarget = rewriteTerm(call.target)
          val rewrittenParams = call.parameters.map(rewriteTerm)
          new Call(call.location, rewrittenTarget, call.method, rewrittenParams)

        case call: CallStatic =>
          val rewrittenParams = call.parameters.map(rewriteTerm)
          new CallStatic(call.location, call.target, call.method, rewrittenParams)

        case binTerm: BinaryTerm =>
          val rewrittenLhs = rewriteTerm(binTerm.lhs)
          val rewrittenRhs = rewriteTerm(binTerm.rhs)
          new BinaryTerm(binTerm.location, binTerm.kind, binTerm.`type`, rewrittenLhs, rewrittenRhs)

        case unTerm: UnaryTerm =>
          val rewrittenOperand = rewriteTerm(unTerm.operand)
          new UnaryTerm(unTerm.location, unTerm.kind, unTerm.`type`, rewrittenOperand)

        case setLocal: SetLocal =>
          val rewrittenValue = rewriteTerm(setLocal.value)
          // If setting a parameter, redirect to loop variable
          if (setLocal.frame == 0 && setLocal.index < paramCount) {
            new SetLocal(loc, 0, loopVarOffset + setLocal.index, setLocal.`type`, rewrittenValue)
          } else {
            new SetLocal(setLocal.location, setLocal.frame, setLocal.index, setLocal.`type`, rewrittenValue)
          }

        case _ =>
          term
      }
    }

    def rewriteStatement(stmt: ActionStatement): ActionStatement = {
      stmt match {
        case ret: Return if ret.term != null =>
          new Return(ret.location, rewriteTerm(ret.term))

        case block: StatementBlock =>
          val rewritten = block.statements.map(rewriteStatement)
          new StatementBlock(block.location, rewritten: _*)

        case ifStmt: IfStatement =>
          val rewrittenCond = rewriteTerm(ifStmt.condition)
          val rewrittenThen = rewriteStatement(ifStmt.thenStatement)
          val rewrittenElse = if (ifStmt.elseStatement != null) {
            rewriteStatement(ifStmt.elseStatement)
          } else null
          new IfStatement(ifStmt.location, rewrittenCond, rewrittenThen, rewrittenElse)

        case exprStmt: ExpressionActionStatement =>
          new ExpressionActionStatement(exprStmt.location, rewriteTerm(exprStmt.term))

        case loop: ConditionalLoop =>
          val rewrittenCond = rewriteTerm(loop.condition)
          val rewrittenStmt = rewriteStatement(loop.stmt)
          new ConditionalLoop(loop.location, rewrittenCond, rewrittenStmt)

        case _ =>
          stmt
      }
    }

    rewriteStatement(body).asInstanceOf[StatementBlock]
  }

  /**
   * Create a default return statement for a given type
   */
  private def createDefaultReturn(returnType: Type): StatementBlock = {
    val loc: Location = null
    val defaultValue: Term = returnType match {
      case TypedAST.BasicType.INT | TypedAST.BasicType.BYTE |
           TypedAST.BasicType.SHORT | TypedAST.BasicType.CHAR =>
        new IntValue(loc, 0)
      case TypedAST.BasicType.LONG =>
        new LongValue(loc, 0L)
      case TypedAST.BasicType.FLOAT =>
        new FloatValue(loc, 0.0f)
      case TypedAST.BasicType.DOUBLE =>
        new DoubleValue(loc, 0.0)
      case TypedAST.BasicType.BOOLEAN =>
        new BoolValue(loc, false)
      case _ =>
        new NullValue(loc)
    }
    new StatementBlock(loc, new Return(loc, defaultValue))
  }

  /**
   * Transform a method body to use state transitions for tail calls
   */
  private def transformMethodBodyForStateMachine(
    body: StatementBlock,
    stateIds: Map[MethodDefinition, Int],
    paramCount: Int
  ): StatementBlock = {
    val stateIdByName = stateIds.map { case (method, id) => method.name -> id }

    def transformStatement(stmt: ActionStatement): ActionStatement = {
      stmt match {
        case ret: Return if ret.term != null =>
          // Check if this is a tail call to another method in the group
          ret.term match {
            case call: Call =>
              call.method match {
                case targetMethod: MethodDefinition if stateIdByName.contains(targetMethod.name) =>
                  // This is a tail call to another method in the group
                  // Transform it to: update parameters, set state, continue
                  val targetStateId = stateIdByName(targetMethod.name)

                  if (config.verbose) {
                    System.err.println(s"[Mutual TCO]     Transforming tail call to ${targetMethod.name} (state $targetStateId)")
                  }

                  // For now, keep the return as-is (will be replaced in full implementation)
                  ret
                case _ =>
                  ret
              }
            case call: CallStatic =>
              call.method match {
                case targetMethod: MethodDefinition if stateIdByName.contains(targetMethod.name) =>
                  val targetStateId = stateIdByName(targetMethod.name)

                  if (config.verbose) {
                    System.err.println(s"[Mutual TCO]     Transforming static tail call to ${targetMethod.name} (state $targetStateId)")
                  }

                  ret
                case _ =>
                  ret
              }
            case _ =>
              ret
          }

        case block: StatementBlock =>
          val transformed = block.statements.map(transformStatement)
          new StatementBlock(block.location, transformed: _*)

        case ifStmt: IfStatement =>
          val transformedThen = transformStatement(ifStmt.thenStatement)
          val transformedElse = if (ifStmt.elseStatement != null) {
            transformStatement(ifStmt.elseStatement)
          } else null
          new IfStatement(ifStmt.location, ifStmt.condition, transformedThen, transformedElse)

        case _ =>
          stmt
      }
    }

    transformStatement(body).asInstanceOf[StatementBlock]
  }
}
