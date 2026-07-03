package onion.compiler.backend.asm

import onion.compiler.TypedAST.*
import org.objectweb.asm.{Opcodes, Type => AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

final class ControlFlowEmitter(
  gen: GeneratorAdapter,
  loops: LoopContext,
  localVars: LocalVarContext,
  asmType: Type => AsmType,
  visitTerm: Term => Unit,
  visitStatement: ActionStatement => Unit
) {
  // Finally blocks of enclosing try-finally regions, innermost first. A `return`
  // inside a try/catch must run these before actually returning (the JVM does not
  // do it for us); each is emitted inline on the return path.
  private var finallyStack: List[() => Unit] = Nil

  private def runPendingFinallies(): Unit = runFinalliesDownTo(0)

  /** Emit the finally blocks entered since the finally-stack was `depth` deep
    * (innermost first), leaving deeper/outer ones pending. Used by return (depth 0)
    * and by break/continue (down to the target loop's entry depth). Each finally is
    * emitted with only the outer ones pending so a finally that itself exits does
    * not recurse into itself. */
  private def runFinalliesDownTo(depth: Int): Unit =
    val pending = finallyStack
    var remaining = pending
    while remaining.length > depth do
      finallyStack = remaining.tail
      remaining.head()
      remaining = remaining.tail
    finallyStack = pending

  private def withFinally[A](emitFinally: () => Unit)(body: => A): A =
    finallyStack = emitFinally :: finallyStack
    try body finally finallyStack = finallyStack.tail

  def emitStatementBlock(node: StatementBlock): Unit =
    for stmt <- node.statements do
      visitStatement(stmt)

  def emitBreak(node: Break): Unit =
    val target = if (node.label != null) loops.endOf(node.label) else loops.currentEnd
    val depth = if (node.label != null) loops.finallyDepthOf(node.label) else loops.currentFinallyDepth
    target match
      case Some(label) =>
        // Run any finally blocks entered inside the loop before jumping out.
        depth.foreach(runFinalliesDownTo)
        gen.goTo(label)
      case None => throw new RuntimeException("Break statement outside of loop")

  def emitContinue(node: Continue): Unit =
    val target = if (node.label != null) loops.startOf(node.label) else loops.currentStart
    val depth = if (node.label != null) loops.finallyDepthOf(node.label) else loops.currentFinallyDepth
    target match
      case Some(label) =>
        depth.foreach(runFinalliesDownTo)
        gen.goTo(label)
      case None => throw new RuntimeException("Continue statement outside of loop")

  def emitExpressionActionStatement(node: ExpressionActionStatement): Unit =
    node.term match
      case cast: AsInstanceOf if !cast.target.`type`.isBasicType && cast.destination.isBasicType =>
        // The result is discarded, so the cast's only effect would be to unbox
        // a reference to a primitive -- which NPEs when a generic method's
        // erased type-variable return is null (e.g. Map[String,Int].put
        // returning the absent previous value). Emit the underlying reference
        // and pop it (one slot) instead of unboxing.
        visitTerm(cast.target)
        gen.pop()
      case term =>
        visitTerm(term)
        term.`type` match
          case t if t.isBottomType => ()
          case BasicType.VOID => ()
          case BasicType.LONG | BasicType.DOUBLE => gen.pop2()
          case _ => gen.pop()

  def emitIfStatement(node: IfStatement): Unit =
    val elseLabel = gen.newLabel()
    val endLabel = gen.newLabel()

    visitTerm(node.condition)
    gen.visitJumpInsn(Opcodes.IFEQ, elseLabel)

    visitStatement(node.thenStatement)
    gen.visitJumpInsn(Opcodes.GOTO, endLabel)

    gen.visitLabel(elseLabel)
    if node.elseStatement != null then
      visitStatement(node.elseStatement)

    gen.visitLabel(endLabel)

  def emitConditionalLoop(node: ConditionalLoop): Unit =
    if node.isPostTest then
      // do { body } while cond — body first; continue jumps to the check
      val bodyLabel = gen.newLabel()
      val condLabel = gen.newLabel()
      val endLabel = gen.newLabel()
      loops.push(node.label, condLabel, endLabel, finallyStack.length)
      try
        gen.visitLabel(bodyLabel)
        visitStatement(node.stmt)
        gen.visitLabel(condLabel)
        visitTerm(node.condition)
        gen.visitJumpInsn(Opcodes.IFNE, bodyLabel)
        gen.visitLabel(endLabel)
      finally
        loops.pop()
    else if node.update != null then
      // for loop: continue runs the update before re-testing the condition
      val condLabel = gen.newLabel()
      val continueLabel = gen.newLabel()
      val endLabel = gen.newLabel()
      loops.push(node.label, continueLabel, endLabel, finallyStack.length)
      try
        gen.visitLabel(condLabel)
        visitTerm(node.condition)
        gen.visitJumpInsn(Opcodes.IFEQ, endLabel)
        visitStatement(node.stmt)
        gen.visitLabel(continueLabel)
        visitStatement(node.update)
        gen.visitJumpInsn(Opcodes.GOTO, condLabel)
        gen.visitLabel(endLabel)
      finally
        loops.pop()
    else
      val startLabel = gen.newLabel()
      val endLabel = gen.newLabel()
      loops.push(node.label, startLabel, endLabel, finallyStack.length)
      try
        gen.visitLabel(startLabel)
        visitTerm(node.condition)
        gen.visitJumpInsn(Opcodes.IFEQ, endLabel)
        visitStatement(node.stmt)
        gen.visitJumpInsn(Opcodes.GOTO, startLabel)
        gen.visitLabel(endLabel)
      finally
        loops.pop()

  def emitNOP(node: NOP): Unit = ()

  def emitReturn(node: Return): Unit =
    if node.term != null then
      visitTerm(node.term)
    if finallyStack.nonEmpty then
      // Run enclosing finally blocks before returning; stash the result across them.
      val slot = if node.term != null then storeResultIfNeeded(node.term.`type`) else None
      runPendingFinallies()
      slot.foreach(gen.loadLocal)
    gen.returnValue()

  private def storeResultIfNeeded(resultType: Type): Option[Int] =
    if (resultType == BasicType.VOID || resultType.isBottomType) None
    else {
      val slot = gen.newLocal(asmType(resultType))
      gen.storeLocal(slot)
      Some(slot)
    }

  private def emitSynchronizedBlock(lockTerm: Term, resultType: Type)(emitBody: => Unit): Unit =
    // Store lock object in a local variable to avoid re-evaluating the expression
    val lockSlot = gen.newLocal(AsmType.getType(classOf[Object]))
    visitTerm(lockTerm)
    gen.storeLocal(lockSlot)

    gen.loadLocal(lockSlot)
    gen.monitorEnter()

    val tryStart = gen.mark()
    // Release the monitor on a non-local exit too: a `return`/`break`/`continue`
    // inside the body must run monitorExit, or the monitor leaks (and the codegen
    // throws IllegalMonitorStateException). Same mechanism as try-finally.
    withFinally(() => { gen.loadLocal(lockSlot); gen.monitorExit() }) { emitBody }
    val tryEnd = gen.mark()

    val resultSlot = storeResultIfNeeded(resultType)

    // Normal exit: release monitor and restore result (if any)
    gen.loadLocal(lockSlot)
    gen.monitorExit()
    resultSlot.foreach(gen.loadLocal)
    val endLabel = gen.newLabel()
    gen.goTo(endLabel)

    // Exception handler: release monitor and rethrow
    // catchException creates label and registers handler at current position
    gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
    gen.loadLocal(lockSlot)
    gen.monitorExit()
    gen.throwException()

    gen.visitLabel(endLabel)

  def emitSynchronized(node: Synchronized): Unit =
    emitSynchronizedBlock(node.term, BasicType.VOID) {
      visitStatement(node.statement)
    }

  def emitSynchronizedTerm(node: SynchronizedTerm): Unit =
    emitSynchronizedBlock(node.lock, node.`type`) {
      // Evaluate body as an expression (leaves result on stack)
      visitTerm(node.body)
    }

  def emitThrow(node: Throw): Unit =
    visitTerm(node.term)
    gen.throwException()

  def emitTry(node: Try): Unit =
    // リソースの初期化
    val resourceSlots = new Array[Int](node.resources.length)
    for i <- node.resources.indices do
      val (binding, init) = node.resources(i)
      visitTerm(init)
      // Use getOrAllocateSlot to register the mapping from typing index to bytecode slot
      // This allows user code in the try block to reference the resource variable
      val slot = localVars.getOrAllocateSlot(binding.index, asmType(binding.tp))
      resourceSlots(i) = slot
      gen.storeLocal(slot)

    // リソースを逆順でclose()するコードを生成するヘルパー
    def emitCloseResources(): Unit =
      for i <- (node.resources.length - 1) to 0 by -1 do
        val (binding, _) = node.resources(i)
        val slot = resourceSlots(i)
        // if (resource != null) resource.close()
        val skipClose = gen.newLabel()
        gen.loadLocal(slot)
        gen.ifNull(skipClose)
        gen.loadLocal(slot)
        // AutoCloseableのclose()を呼び出し
        val closeMethod = AsmType.getType(classOf[AutoCloseable]).getInternalName
        gen.invokeInterface(
          AsmType.getType(classOf[AutoCloseable]),
          new org.objectweb.asm.commons.Method("close", "()V")
        )
        gen.visitLabel(skipClose)

    // ユーザー定義のfinallyと組み合わせ
    // Java spec: resources are closed before finally block
    def emitFinallyWithResources(): Unit =
      emitCloseResources()
      if (node.finallyStatement != null) then visitStatement(node.finallyStatement)

    // Emit the protected region and return its [start, end) labels. A NOP is
    // emitted at the top so the region is never empty: an empty try body would
    // make start == end, which the JVM rejects at class-load time with
    // "Illegal exception table range". The NOP is unreachable-cost (never
    // throws) and keeps the exception table well-formed for empty `try {}`.
    def markTryRegion(): (org.objectweb.asm.Label, org.objectweb.asm.Label) =
      val start = gen.mark()
      gen.visitInsn(Opcodes.NOP)
      visitStatement(node.tryStatement)
      val end = gen.mark()
      (start, end)

    if (node.resources.isEmpty && node.finallyStatement == null) {
      // Simple try-catch without finally and resources
      val (tryStart, tryEnd) = markTryRegion()

      val endLabel = gen.newLabel()
      gen.goTo(endLabel)

      for i <- node.catchTypes.indices do
        val catchType = node.catchTypes(i)
        val catchStmt = node.catchStatements(i)
        gen.catchException(tryStart, tryEnd, asmType(catchType.tp))
        val slot = localVars.allocateSlot(catchType.index, asmType(catchType.tp))
        gen.storeLocal(slot)
        visitStatement(catchStmt)
        gen.goTo(endLabel)

      gen.visitLabel(endLabel)
    } else if (node.catchTypes.length == 0) {
      // Try-finally (with or without resources)
      val (tryStart, tryEnd) = withFinally(() => emitFinallyWithResources()) { markTryRegion() }

      // Normal completion: execute finally and jump to end
      emitFinallyWithResources()
      val endLabel = gen.newLabel()
      gen.goTo(endLabel)

      // Exception handler: save exception, execute finally, rethrow
      gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
      val exSlot = gen.newLocal(AsmType.getType(classOf[Throwable]))
      gen.storeLocal(exSlot)
      emitFinallyWithResources()
      gen.loadLocal(exSlot)
      gen.throwException()

      gen.visitLabel(endLabel)
    } else {
      // Try-catch-finally (with or without resources)
      val (tryStart, tryEnd) = withFinally(() => emitFinallyWithResources()) { markTryRegion() }

      // Normal completion: execute finally and jump to end
      emitFinallyWithResources()
      val endLabel = gen.newLabel()
      gen.goTo(endLabel)

      // The user finally (resources are already closed before a catch runs).
      def emitCatchFinally(): Unit =
        if (node.finallyStatement != null) then visitStatement(node.finallyStatement)

      // Catch handlers for specific exceptions
      for i <- node.catchTypes.indices do
        val catchType = node.catchTypes(i)
        val catchStmt = node.catchStatements(i)
        gen.catchException(tryStart, tryEnd, asmType(catchType.tp))
        val slot = localVars.allocateSlot(catchType.index, asmType(catchType.tp))
        gen.storeLocal(slot)
        // Close resources before catch block (Java try-with-resources spec)
        emitCloseResources()
        // A return inside the catch must also run the user finally.
        withFinally(() => emitCatchFinally()) { visitStatement(catchStmt) }
        // Normal catch completion: execute user finally
        emitCatchFinally()
        gen.goTo(endLabel)

      // Catch-all handler for uncaught exceptions (finally + rethrow)
      gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
      val exSlot = gen.newLocal(AsmType.getType(classOf[Throwable]))
      gen.storeLocal(exSlot)
      emitFinallyWithResources()
      gen.loadLocal(exSlot)
      gen.throwException()

      gen.visitLabel(endLabel)
    }
}
