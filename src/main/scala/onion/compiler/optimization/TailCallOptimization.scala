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
      case body: GuardedBody =>
        checkStatementForTailCall(body.statements.last, method)
      case block: StatementBlock =>
        blockHasTailCall(block, method)
      case ifStmt: IfStatement =>
        checkStatementForTailCall(ifStmt.thenStatement, method) ||
        (if (ifStmt.elseStatement != null) checkStatementForTailCall(ifStmt.elseStatement, method) else false)
      case _ =>
        false
    }
  }

  /**
   * A block has a tail call when a nested statement does, or when it ends in `result = f(..)`:
   * the body of a `select` case with bindings (`case p is T:`, `case R(a, b):`) is such a block,
   * where a literal case's body sits directly under the `if` (#2350).
   */
  private def blockHasTailCall(block: StatementBlock, method: MethodDefinition): Boolean =
    containsTailCall(block.statements.toSeq, method) ||
      (block.statements.lastOption match {
        case Some(last: ExpressionActionStatement) if last.term.isInstanceOf[SetLocal] =>
          checkStatementForTailCall(last, method)
        case _ => false
      })

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
      case body: GuardedBody =>
        checkStatementForTailCall(body.statements.last, method)
      case block: StatementBlock =>
        blockHasTailCall(block, method)
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
      case call: Call if unboxedJoin(call).isDefined =>
        // `: Int = if c { boxed } else { f(..) }`: the whole `if` is unboxed (#2406)
        checkStatementForTailCall(unboxedJoin(call).get, method)
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
        ret.term match {
          case begin: Begin if begin.terms.nonEmpty =>
            // A Begin evaluates its terms in order and yields the last one, so only the
            // last term is in tail position: `return { t1; ...; tn }` becomes
            // `t1; ...; return tn`. A self call in an earlier term (`{ val x = f(n - 1);
            // x + 1 }`) is not a tail call and stays a call.
            trace(s"[TCO transformStatement] Return: Begin with ${begin.terms.length} terms")
            transformStatement(sinkReturnIntoBegin(begin), method, slots)
          case call: Call if unboxedJoin(call).isDefined =>
            trace(s"[TCO transformStatement] Return: unboxed if/select join")
            transformStatement(unboxedJoin(call).get, method, slots)
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
   * `return { t1; ...; tn }` as `t1; ...; return tn`. An if/select expression's value
   * (`= if c { a } else { f(..) }`) is typed as `{ <branches assign tmp>; tmp }`; there
   * the return is sunk into each branch instead (`tmp = value; return tmp` returns
   * `value`), so a self call at a branch's end becomes a tail call (#2330).
   */
  private def sinkReturnIntoBegin(begin: Begin): ActionStatement = {
    def asStatement(t: Term): ActionStatement = t match {
      case st: StatementTerm => st.statement
      case other => new ExpressionActionStatement(other.location, other)
    }
    val terms = begin.terms.toSeq
    val (prefix, tail) = terms match {
      case init :+ (st: StatementTerm) :+ (result: ResultRefLocal) =>
        val (sunk, alwaysReturns) = sinkReturn(st.statement, result)
        (init, if (alwaysReturns) Seq(sunk) else Seq(sunk, new Return(null, result)))
      case init :+ last =>
        (init, Seq(new Return(null, last)))
    }
    new StatementBlock(begin.location, (prefix.map(asStatement) ++ tail): _*)
  }

  private val unboxMethodNames = Set(
    "booleanValue", "byteValue", "shortValue", "charValue",
    "intValue", "longValue", "floatValue", "doubleValue"
  )

  /** Whether `owner` is the `java.lang` box class of the primitive `primitive`. */
  private def isBoxClassOf(owner: Type, primitive: Type): Boolean = (owner, primitive) match {
    case (ct: ClassType, b: BasicType) => toolbox.Boxing.boxedClassName(b).contains(ct.name)
    case _ => false
  }

  /** `x.intValue()` (and the other primitive unboxings) on the `java.lang` box of the result. */
  private def isUnboxCall(call: Call): Boolean =
    call.parameters.isEmpty && unboxMethodNames.contains(call.method.name) &&
      isBoxClassOf(call.method.affiliation, call.`type`) &&
      isBoxClassOf(call.target.`type`, call.`type`)

  /** `Integer.valueOf(i)` (and the other boxings), the box of `primitive`; never a user method. */
  private def isBoxCall(term: Term, primitive: Type): Boolean = term match {
    case box: CallStatic =>
      box.method.name == "valueOf" && box.parameters.length == 1 &&
        box.parameters(0).`type` == primitive && isBoxClassOf(box.method.affiliation, primitive)
    case _ => false
  }

  /**
   * `return unbox({ <branches assign tmp>; tmp })`, the shape of an if/select expression whose
   * boxed join (`Integer` and `Int` join to `Integer`) is unboxed to the method's primitive
   * result, as `return`s sunk into the branches: `tmp = v` becomes `return unbox(v)`, and
   * `tmp = box(f(..))` becomes `return f(..)`, a tail call (#2406). The unbox still runs in
   * return position, where nothing in this method can catch it, so a null box throws from
   * the same place as before. `None` for any other shape.
   */
  private def unboxedJoin(call: Call): Option[ActionStatement] = {
    if (!isUnboxCall(call)) return None
    call.target match {
      case begin: Begin =>
        begin.terms.toSeq match {
          case init :+ (st: StatementTerm) :+ (result: ResultRefLocal) =>
            def unboxed(value: Term): Term = value match {
              case box: CallStatic if isBoxCall(box, call.`type`) => box.parameters(0)
              case other => new Call(other.location, other, call.method, Array.empty[Term])
            }
            val (sunk, alwaysReturns) = sinkReturn(st.statement, result, unboxed)
            def asStatement(t: Term): ActionStatement = t match {
              case s: StatementTerm => s.statement
              case other => new ExpressionActionStatement(other.location, other)
            }
            val tail =
              if (alwaysReturns) Seq(sunk)
              else Seq(sunk, new Return(null, new Call(call.location, result, call.method, Array.empty[Term])))
            Some(new StatementBlock(begin.location, (init.map(asStatement) ++ tail): _*))
          case _ => None
        }
      case _ => None
    }
  }

  /**
   * Rewrites a branch's final `result = value` into `return value`. Returns the new
   * statement and whether every path through it now returns (or throws); a path that
   * does not falls through to the caller's `return result`.
   */
  private def sinkReturn(
    stmt: ActionStatement,
    result: RefLocal,
    returned: Term => Term = identity
  ): (ActionStatement, Boolean) = stmt match {
    case exprStmt: ExpressionActionStatement =>
      exprStmt.term match {
        case set: SetLocal if set.frame == result.frame && set.index == result.index =>
          (new Return(exprStmt.location, returned(set.value)), true)
        case _ => (stmt, false)
      }
    case guarded: GuardedCaseBlock =>
      // statements: taken = false; the case; if (!taken) rest. Sinking into the case is safe:
      // once it ran, nothing after it does. Not claiming "always returns" keeps the caller's
      // trailing `return result`, which is harmless.
      val Array(init, matched, rest) = guarded.statements
      val (sunkMatched, _) = sinkReturn(matched, result, returned)
      val (sunkRest, _) = sinkReturn(rest, result, returned)
      (new GuardedCaseBlock(guarded.location, init, sunkMatched, sunkRest), false)
    case block: StatementBlock if block.statements.nonEmpty =>
      val (last, returns) = sinkReturn(block.statements.last, result, returned)
      (new StatementBlock(block.location, (block.statements.init.toSeq :+ last): _*), returns)
    case ifStmt: IfStatement =>
      val (thenStmt, thenReturns) = sinkReturn(ifStmt.thenStatement, result, returned)
      if (ifStmt.elseStatement == null) {
        (new IfStatement(ifStmt.location, ifStmt.condition, thenStmt, null), false)
      } else {
        val (elseStmt, elseReturns) = sinkReturn(ifStmt.elseStatement, result, returned)
        (new IfStatement(ifStmt.location, ifStmt.condition, thenStmt, elseStmt), thenReturns && elseReturns)
      }
    case _: Return | _: Throw => (stmt, true)
    case _ => (stmt, false)
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
          // Only the last term is the Begin's value
          if (begin.terms.isEmpty) None else extractCall(begin.terms.last, depth + 1)
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

    val callArgs: Array[Term] = actualCall match {
      case call: Call => call.parameters
      case call: CallStatic => call.parameters
      case _ => Array.empty
    }
    // An omitted defaulted argument is computed by its default method from the new values
    // of the arguments before it, which the multi-argument path below holds in temps.
    val newArgs: Array[Term] = callArgs.zipWithIndex.map {
      case (d: DefaultArgument, i) =>
        val receiver = if (Modifier.isStatic(d.method.modifier)) null else new This(d.method.classType)
        val earlier = Array.tabulate[Term](i)(j => new RefLocal(null, 0, slots.tempVarOffset + j, slots.paramTypes(j)))
        d.invocation(receiver, earlier)
      case (arg, _) => arg
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
