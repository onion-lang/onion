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
import onion.compiler.TermWalk
import scala.collection.mutable
import scala.util.boundary, boundary.break

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

  private inline def trace(message: => String): Unit =
    if (config.verbose) System.err.println(message)

  private val warnings = mutable.ArrayBuffer[CompileWarning]()

  /** Warnings accumulated by the most recent `process` call (e.g. a `@TailRecursive`
    * group that could not be optimized). Read after `process` returns.
    */
  def collectedWarnings: Seq[CompileWarning] = warnings.toSeq

  def newEnvironment(source: Seq[ClassDefinition]): Environment =
    new OptimizationEnvironment

  def processBody(
    source: Seq[ClassDefinition],
    environment: OptimizationEnvironment
  ): Seq[ClassDefinition] = {
    warnings.clear()
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

    // Normalize method bodies: sink returns into if-expression branches.
    // The type checker compiles `if c { t1 } else { t2 }` as a value expression
    // to  Begin([StatementTerm(IfStatement(c, assign(v,t1), assign(v,t2))), RefLocal(v)]),
    // which buries the tail call inside an assignment.  Rewrite each such
    // Return(Begin(...)) into IfStatement(c, Return(t1), Return(t2)) so that
    // findTailCalls and transformMethodBodyForStateMachine can see the tail call.
    annotatedMethods.foreach { method =>
      if (method.getBlock != null) {
        method.setBlock(normalizeMethodBody(method.getBlock))
      }
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
          trace(s"[Mutual TCO] Detected mutual recursion group: $methodNames")
        }

        // Validate group can be optimized
        validateGroup(group) match {
          case Some(error) =>
            if (config.verbose) {
              trace(s"[Mutual TCO] Cannot optimize: $error")
            }
            reportIneffective(classDef, group, error)
          case None =>
            if (config.verbose) {
              trace(s"[Mutual TCO] Optimization will be applied")
            }
            transformGroup(classDef, group)
        }
      }
    }

    classDef
  }

  // ── Return-sinking normalization ──────────────────────────────────────────
  // Converts `Return(Begin([StatementTerm(IfStatement(c, assign(v,t1), assign(v,t2))), RefLocal(v)]))`
  // (the TypedAST form of a non-void if-expression used as a return value) into
  // `IfStatement(c, Return(t1), Return(t2))` so that downstream analysis and
  // transformation can see the tail calls in each branch.

  private def normalizeMethodBody(block: StatementBlock): StatementBlock = {
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
  private def validateGroup(group: Seq[MethodDefinition]): Option[String] = boundary {
    // Check 1: All methods must be private (to avoid breaking public API)
    val nonPrivateMethods = group.filterNot(isPrivate)
    if (nonPrivateMethods.nonEmpty) {
      val names = nonPrivateMethods.map(_.name).mkString(", ")
      break(Some(s"All methods must be private for mutual recursion optimization. Non-private: $names"))
    }

    // Check 2: All methods must have same return type
    val returnTypes = group.map(_.returnType.name).toSet
    if (returnTypes.size > 1) {
      break(Some(s"Methods have different return types: ${returnTypes.mkString(", ")}"))
    }

    // Check 3: All methods must be in same class (already guaranteed by our collection)

    // Check 4: All methods must share one parameter list (count and per-position types),
    // since the state machine has a single set of loop variables shared by every member.
    val paramCounts = group.map(_.arguments.length).toSet
    if (paramCounts.size > 1) {
      break(Some(s"Methods have different parameter counts: ${paramCounts.mkString(", ")}"))
    }
    val paramCount = group.head.arguments.length
    val incompatibleParam = (0 until paramCount).find { paramIdx =>
      group.map(_.arguments(paramIdx).name).toSet.size > 1
    }
    incompatibleParam.foreach { paramIdx =>
      val paramTypes = group.map(_.arguments(paramIdx).name).toSet
      break(Some(s"Methods have incompatible parameter lists: parameter $paramIdx has different types: ${paramTypes.mkString(", ")}"))
    }

    // Check 5: All tail calls must be to methods in the group
    val validationError: Option[String] = None
    val iterator = group.iterator
    while (iterator.hasNext && validationError.isEmpty) {
      val method = iterator.next()
      val tailCalls = TailCallGraphAnalysis.findTailCalls(method)
      val externalCalls = tailCalls.filterNot(callee => group.exists(_ eq callee))
      if (externalCalls.nonEmpty) {
        break(Some(s"Method ${method.name} has tail calls to non-group methods: ${externalCalls.map(signature).mkString(", ")}"))
      }
    }

    // Check 6: No member may declare a local variable beyond its parameter list.
    // The merged state machine keeps each parameter in its original slot; a local
    // declared in the body (e.g. `val next: Box = ...`) keeps its original slot index,
    // which collides with the state/temp variable slots the merged method
    // introduces starting at exactly that index once every member's body is spliced
    // into one shared frame -- producing invalid bytecode (a JVM VerifyError at
    // class-load time) instead of either compiling correctly or failing to compile.
    // The temporaries that hold a tail call's out-of-order named arguments are the one
    // exception: the transition stores those values in its own slots instead (#2800).
    val paramCountForLocals = group.head.arguments.length
    val declaresExtraLocal = group.find { m =>
      val temporaries = argumentTemporaries(m.getBlock, group, paramCountForLocals)
      referencesLocalBeyondParams(m.getBlock, paramCountForLocals, temporaries)
    }
    declaresExtraLocal.foreach { method =>
      // A nested call that names arguments out of order gets temporaries too; say so
      // rather than blame the user for a local they never declared.
      val extra = localsBeyondParams(method.getBlock, paramCountForLocals)
      if (extra.nonEmpty && extra.subsetOf(callArgumentTemporaries(method.getBlock, paramCountForLocals)))
        break(Some(s"a call in method ${method.name} names arguments out of parameter order, and running " +
          "them in written order needs a temporary the generated state machine method has no slot for " +
          "(write that call's arguments in parameter order)"))
      break(Some(s"method ${method.name} declares a local variable beyond its parameter list, " +
        "which the generated state machine method cannot give a non-overlapping slot"))
    }

    validationError
  }

  // `a(String)`: a bare name cannot tell an out-of-group overload from a group member.
  private def signature(method: MethodDefinition): String =
    s"${method.name}(${method.arguments.map(sourceName).mkString(", ")})"

  // A boxed primitive in a type argument is written as the primitive (`List[Int]`).
  private def typeArgName(tpe: TypedAST.Type): String = sourceName(tpe) match {
    case "Integer"   => "Int"
    case "Character" => "Char"
    case other       => other
  }

  // The way the source writes a type: simple class names, `Int[]` for arrays.
  private def sourceName(tpe: TypedAST.Type): String = tpe match {
    case a: TypedAST.ArrayType        => sourceName(a.component) + "[]" * a.dimension
    case n: TypedAST.NullableType     => sourceName(n.innerType) + "?"
    case c: TypedAST.AppliedClassType =>
      val raw = c.raw.name.substring(c.raw.name.lastIndexOf('.') + 1)
      if (c.typeArguments.isEmpty) raw else raw + c.typeArguments.map(typeArgName).mkString("[", ", ", "]")
    case t: TypedAST.ClassType        => t.name.substring(t.name.lastIndexOf('.') + 1)
    case other                        => other.displayName
  }

  /**
   * Warn that a detected `@TailRecursive` mutual-recursion group could not be
   * optimized, so callers relying on the annotation for stack safety find out
   * at compile time instead of via a runtime StackOverflowError.
   */
  private def reportIneffective(classDef: ClassDefinition, group: Seq[MethodDefinition], reason: String): Unit = {
    val sourceFile = Option(classDef.getSourceFile).getOrElse("")
    val groupNames = group.map(_.name).mkString(", ")
    group.foreach { method =>
      val message = s"method '${method.name}' is annotated @TailRecursive but its mutual-recursion " +
        s"group ($groupNames) could not be optimized: $reason; without the state-machine rewrite, " +
        s"deep recursion between these methods can still overflow the JVM stack"
      warnings += CompileWarning(sourceFile, method.location, WarningCategory.IneffectiveTailRecursive, message)
    }
  }

  /**
   * Whether `body` still touches the enclosing instance -- directly as `this`/the
   * outer-class `this` of a nested class, or (via TermWalk's normal descent into every
   * term's children) as the implicit receiver of a field access or instance method call.
   * Used to catch, before it reaches BytecodeGeneration, a state-machine body that would
   * need a receiver despite being emitted as a static method.
   */
  private def referencesThis(body: StatementBlock): Boolean =
    TermWalk.existsIn(body) {
      case _: TypedAST.This      => true
      case _: TypedAST.OuterThis => true
      case _                     => false
    }

  /**
   * Whether `body` reads or writes a local-variable slot beyond the method's own
   * `paramCount` parameters -- i.e. an ordinary `val`/`var` declared in the body (or any
   * other local the method's own frame allocated past its parameters). See Check 6 in
   * `validateGroup` for why such a local cannot safely share the merged state machine's
   * frame.
   */
  private def referencesLocalBeyondParams(body: StatementBlock, paramCount: Int, except: Set[Int]): Boolean =
    TermWalk.existsIn(body) {
      case ref: TypedAST.RefLocal => ref.frame == 0 && ref.index >= paramCount && !except(ref.index)
      case set: TypedAST.SetLocal => set.frame == 0 && set.index >= paramCount && !except(set.index)
      case _                      => false
    }

  private def localsBeyondParams(body: StatementBlock, paramCount: Int): Set[Int] = {
    val found = mutable.Set[Int]()
    TermWalk.existsIn(body) {
      case ref: TypedAST.RefLocal if ref.frame == 0 && ref.index >= paramCount => found += ref.index; false
      case set: TypedAST.SetLocal if set.frame == 0 && set.index >= paramCount => found += set.index; false
      case _ => false
    }
    found.toSet
  }

  /** The temporaries of every call in `body` whose named arguments are out of order. */
  private def callArgumentTemporaries(body: StatementBlock, paramCount: Int): Set[Int] = {
    val found = mutable.Set[Int]()
    def add(params: Array[Term]): Boolean = {
      if (params != null) argumentStores(params, paramCount).foreach(found ++= _._2)
      false
    }
    TermWalk.existsIn(body) {
      case c: Call       => add(c.parameters)
      case c: SafeCall   => add(c.parameters)
      case c: CallStatic => add(c.parameters)
      case c: CallSuper  => add(c.params)
      case n: NewObject  => add(n.parameters)
      case _             => false
    }
    found.toSet
  }

  /**
   * The local slots `body` uses only to hold out-of-order named arguments of the tail
   * calls `transformMethodBodyForStateMachine` turns into state transitions. Those calls
   * are the `return`s it reaches through blocks and `if` branches.
   */
  private def argumentTemporaries(body: StatementBlock, group: Seq[MethodDefinition], paramCount: Int): Set[Int] = {
    def inGroup(target: Method): Boolean = target match {
      case m: MethodDefinition => group.exists(_ eq m)
      case _                   => false
    }
    def tailCallParams(term: Term): Option[Array[Term]] = term match {
      case call: Call if inGroup(call.method)             => Some(call.parameters)
      case call: CallStatic if inGroup(call.method)       => Some(call.parameters)
      case _                                              => None
    }
    def visit(stmt: ActionStatement): Set[Int] = stmt match {
      case ret: Return if ret.term != null =>
        tailCallParams(ret.term)
          .filterNot(_.exists(_.isInstanceOf[DefaultArgument]))
          .flatMap(params => argumentStores(params, paramCount))
          .map(_._2)
          .getOrElse(Set.empty)
      case block: StatementBlock => block.statements.toSeq.flatMap(visit).toSet
      case ifStmt: IfStatement =>
        visit(ifStmt.thenStatement) ++ (if (ifStmt.elseStatement != null) visit(ifStmt.elseStatement) else Set.empty)
      case _ => Set.empty
    }
    visit(body)
  }

  /**
   * A tail call's arguments as `(parameter index, value)` pairs, in the order the call
   * evaluates them, with the temporaries' slots.
   *
   * Named arguments written out of parameter order arrive stored in temporaries
   * (`ArgumentHelpers.evaluateInWrittenOrder`): one argument is a `Begin` of
   * `SetLocal(temporary, value)` stores, in written order, and each reordered parameter
   * reads its temporary. The values go straight to their parameters instead, in the same
   * order, so the temporaries disappear. `None` when the arguments use a local slot in
   * any other way; the check that follows then reports it.
   */
  private def argumentStores(params: Array[Term], paramCount: Int): Option[(Seq[(Int, Term)], Set[Int])] = {
    def isTemporaryStore(t: Term): Boolean = t match {
      case set: SetLocal => set.frame == 0 && set.index >= paramCount
      case _             => false
    }
    val begins = params.indices.filter { i =>
      params(i) match {
        case b: Begin => b.terms.length >= 2 && b.terms.init.forall(isTemporaryStore)
        case _        => false
      }
    }
    if (begins.isEmpty) return Some((params.indices.map(i => (i, params(i))), Set.empty))
    if (begins.size > 1) return None
    val first = begins.head
    val begin = params(first).asInstanceOf[Begin]
    val stores = begin.terms.init.map(_.asInstanceOf[SetLocal]).toSeq
    val temporaries = stores.map(_.index)
    if (temporaries.distinct.size != temporaries.size) return None
    def readsTemporary(t: Term): Option[Int] = t match {
      case ref: RefLocal if ref.frame == 0 && temporaries.contains(ref.index) => Some(ref.index)
      case _                                                                   => None
    }
    // Each temporary is read by exactly one parameter: `first` (through the Begin's last
    // term) or another one, directly.
    val slotOf: Map[Int, Int] = params.indices.flatMap { i =>
      readsTemporary(if (i == first) begin.terms.last else params(i)).map(_ -> i)
    }.toMap
    val reads = params.indices.count(i => readsTemporary(if (i == first) begin.terms.last else params(i)).isDefined)
    if (slotOf.size != temporaries.size || reads != temporaries.size) return None
    val steps = params.indices.flatMap { i =>
      if (i == first)
        stores.map(set => (slotOf(set.index), set.value)) ++
          (if (readsTemporary(begin.terms.last).isEmpty) Seq((first, begin.terms.last)) else Nil)
      else if (readsTemporary(params(i)).isDefined) Nil
      else Seq((i, params(i)))
    }
    Some((steps, temporaries.toSet))
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
        trace(s"[Mutual TCO]   State $stateId: ${method.name}")
      }
    }

    // Parameter compatibility is already guaranteed by validateGroup before this is called.
    val paramCount = group.head.arguments.length

    // Step 2: Create state machine method name and structure
    val stateMachineMethodName = group.map(_.name).mkString("_")
    val returnType = group.head.returnType
    val paramTypes = group.head.arguments

    if (config.verbose) {
      trace(s"[Mutual TCO] Creating state machine method: $stateMachineMethodName")
      trace(s"[Mutual TCO]   Parameters: ${paramTypes.map(_.name).mkString(", ")}")
      trace(s"[Mutual TCO]   Return type: ${returnType.name}")
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

    // Guard: the state machine method is always emitted `private static` (it has no
    // enclosing instance -- the whole point is a flat loop with no call-stack growth).
    // transformMethodBodyForStateMachine only rewrites *tail* calls to other members of
    // the group; anything else in the original bodies -- a field read, `this` passed as
    // a value, or a call to another instance method, whether reached in tail position or
    // not -- survives verbatim into the generated body still expecting a receiver. With
    // no receiver on a static method, that used to crash BytecodeGeneration with
    // "no 'this' pointer within static method" (an I0000 internal error) instead of
    // compiling or reporting a normal diagnostic. Detect any leftover `this` in the
    // generated body up front and fall back to the ordinary (non-tail-optimized)
    // methods, the same degraded-but-safe path the other validateGroup checks use.
    if (referencesThis(stateMachineMethod.getBlock)) {
      reportIneffective(
        classDef,
        group,
        "the group reads instance state (a field, or a call to another instance method, " +
          "via an implicit or explicit `this`), which the generated state machine method " +
          "-- always `private static` -- cannot access"
      )
      return
    }

    // Step 4: Add state machine method to class
    classDef.methods_.add(stateMachineMethod)
    if (config.verbose) {
      trace(s"[Mutual TCO] Added state machine method to class")
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
        trace(s"[Mutual TCO] Replaced ${method.name} with forwarder (state $stateId)")
      }
    }

    if (config.verbose) {
      trace(s"[Mutual TCO] State machine optimization complete")
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
      trace(s"[Mutual TCO] Generated state machine method: $methodName")
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
    // 0..(paramCount-1): parameters, updated in place by each tail call
    // paramCount: state parameter, likewise updated in place
    // (paramCount+1)..: temporaries for a tail call's new argument values
    // A tail call assigns the parameter slots themselves (the JVM allows it), so every
    // read of a parameter -- in a `try`, a lambda's capture, anything -- sees the
    // current round's value with no rewriting. Copying the parameters into separate
    // loop variables and redirecting reads missed every node kind the rewrite did not
    // list, and those reads kept the first call's value (#2334).
    val loopVarOffset = 0
    val stateLoopVarIndex = paramCount

    // Step 1: Create if-else chain for state dispatch
    val stateDispatch = createStateDispatch(
      group,
      stateIds,
      loopVarOffset,
      stateLoopVarIndex,
      paramCount,
      returnType
    )

    // Step 2: Create infinite loop
    val trueValue = new BoolValue(loc, true)
    val loop = new ConditionalLoop(loc, trueValue, stateDispatch)

    new StatementBlock(loc, loop)
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
      trace(s"[Mutual TCO]   Building if-else chain for ${group.size} states")
    }

    // Process methods in reverse order to build the chain from bottom up
    group.toSeq.reverse.foreach { method =>
      val stateId = stateIds(method)
      val stateIdValue = new IntValue(loc, stateId)

      if (config.verbose) {
        trace(s"[Mutual TCO]   Processing state $stateId (${method.name})")
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
        trace(s"[Mutual TCO]     Transformed body has ${transformedBody.statements.length} statements")
      }

      // Create if statement
      val ifStmt = new IfStatement(loc, condition, transformedBody, currentElse)
      currentElse = ifStmt
    }

    if (config.verbose) {
      trace(s"[Mutual TCO]   If-else chain complete")
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
    // Overloads share a name, so a call is matched to its member by identity.
    def stateIdOf(target: MethodDefinition): Option[Int] =
      stateIds.collectFirst { case (method, id) if method eq target => id }

    def transformStatement(stmt: ActionStatement): ActionStatement = {
      stmt match {
        case ret: Return if ret.term != null =>
          // Check if this is a tail call to another method in the group
          ret.term match {
            case call: Call =>
              call.method match {
                case targetMethod: MethodDefinition if stateIdOf(targetMethod).isDefined && !call.parameters.exists(_.isInstanceOf[DefaultArgument]) =>
                  // Transform to: update params, set state, continue
                  if (config.verbose) {
                    trace(s"[Mutual TCO]       Found tail call: ${targetMethod.name} -> state ${stateIdOf(targetMethod).get}")
                  }
                  transformTailCallToStateTransition(
                    call.parameters,
                    stateIdOf(targetMethod).get,
                    loopVarOffset,
                    stateLoopVarIndex,
                    call.parameters.map(_.`type`)
                  )
                case _ =>
                  ret
              }
            case call: CallStatic =>
              call.method match {
                case targetMethod: MethodDefinition if stateIdOf(targetMethod).isDefined && !call.parameters.exists(_.isInstanceOf[DefaultArgument]) =>
                  if (config.verbose) {
                    trace(s"[Mutual TCO]       Found static tail call: ${targetMethod.name} -> state ${stateIdOf(targetMethod).get}")
                  }
                  transformTailCallToStateTransition(
                    call.parameters,
                    stateIdOf(targetMethod).get,
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
        trace(s"[Mutual TCO]         transformStatementsInBlock: ${stmts.length} statements")
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
              trace(s"[Mutual TCO]         Last statement is tail call return!")
            }
            // Check if second-to-last is an if statement with return in then branch
            if (lastIdx > 0) {
              stmts(lastIdx - 1) match {
                case ifStmt: IfStatement if ifStmt.elseStatement == null =>
                  if (config.verbose) {
                    trace(s"[Mutual TCO]         Merging tail call into else branch of if statement")
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
                    trace(s"[Mutual TCO]         Second-to-last is not suitable if statement: ${other.getClass.getSimpleName}")
                  }
                  stmts.map(transformStatement)
              }
            } else {
              stmts.map(transformStatement)
            }
          case _ =>
            if (config.verbose) {
              trace(s"[Mutual TCO]         Last statement is not tail call: ${last.getClass.getSimpleName}")
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
              val inGroup = stateIdOf(targetMethod).isDefined
              if (config.verbose) {
                trace(s"[Mutual TCO]         Checking Call: ${targetMethod.name}, in group: $inGroup")
              }
              inGroup
            case _ => false
          }
        case call: CallStatic =>
          call.method match {
            case targetMethod: MethodDefinition =>
              val inGroup = stateIdOf(targetMethod).isDefined
              if (config.verbose) {
                trace(s"[Mutual TCO]         Checking CallStatic: ${targetMethod.name}, in group: $inGroup")
              }
              inGroup
            case _ => false
          }
        case _ => false
      }
      result
    }

    // Transform statements, merging tail calls into if-else structure
    val transformedStatements = transformStatementsInBlock(body.statements.toSeq)
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

    // Step 1: Assign new argument values to temporary variables, in the order the call
    // evaluates them (named arguments run in written order, #2800)
    val stores = argumentStores(newArgs, stateLoopVarIndex).map(_._1)
      .getOrElse(newArgs.indices.map(i => (i, newArgs(i))))
    for ((i, newValue) <- stores) {
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
}
