package onion.compiler.bytecode

import onion.compiler.TypedAST.*
import org.objectweb.asm.{Opcodes, Type => AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

final class ControlFlowEmitter(
  gen: GeneratorAdapter,
  loops: LoopContext,
  asmType: Type => AsmType,
  visitTerm: Term => Unit,
  visitStatement: ActionStatement => Unit
) {
  def emitStatementBlock(node: StatementBlock): Unit =
    for stmt <- node.statements do
      visitStatement(stmt)

  def emitBreak(node: Break): Unit =
    loops.currentEnd match
      case Some(label) => gen.goTo(label)
      case None => throw new RuntimeException("Break statement outside of loop")

  def emitContinue(node: Continue): Unit =
    loops.currentStart match
      case Some(label) => gen.goTo(label)
      case None => throw new RuntimeException("Continue statement outside of loop")

  def emitExpressionActionStatement(node: ExpressionActionStatement): Unit =
    visitTerm(node.term)
    node.term.`type` match
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
    val startLabel = gen.newLabel()
    val endLabel = gen.newLabel()
    loops.push(startLabel, endLabel)
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
    gen.returnValue()

  def emitSynchronized(node: Synchronized): Unit =
    visitTerm(node.term)
    gen.monitorEnter()

    val tryStart = gen.mark()
    visitStatement(node.statement)
    val tryEnd = gen.mark()

    visitTerm(node.term)
    gen.monitorExit()
    val endLabel = gen.newLabel()
    gen.goTo(endLabel)

    val handlerStart = gen.mark()
    visitTerm(node.term)
    gen.monitorExit()
    gen.throwException()

    gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
    gen.visitLabel(endLabel)

  def emitThrow(node: Throw): Unit =
    visitTerm(node.term)
    gen.throwException()

  def emitTry(node: Try): Unit =
    if (node.finallyStatement == null) {
      // Simple try-catch without finally
      val tryStart = gen.mark()
      visitStatement(node.tryStatement)
      val tryEnd = gen.mark()

      val endLabel = gen.newLabel()
      gen.goTo(endLabel)

      for i <- node.catchTypes.indices do
        val catchType = node.catchTypes(i)
        val catchStmt = node.catchStatements(i)
        gen.catchException(tryStart, tryEnd, asmType(catchType.tp))
        val slot = gen.newLocal(asmType(catchType.tp))
        gen.storeLocal(slot)
        visitStatement(catchStmt)
        gen.goTo(endLabel)

      gen.visitLabel(endLabel)
    } else if (node.catchTypes.length == 0) {
      // Simple try-finally without catch
      val tryStart = gen.mark()
      visitStatement(node.tryStatement)
      val tryEnd = gen.mark()

      // Normal completion: execute finally and jump to end
      visitStatement(node.finallyStatement)
      val endLabel = gen.newLabel()
      gen.goTo(endLabel)

      // Exception handler: save exception, execute finally, rethrow
      gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
      val exSlot = gen.newLocal(AsmType.getType(classOf[Throwable]))
      gen.storeLocal(exSlot)
      visitStatement(node.finallyStatement)
      gen.loadLocal(exSlot)
      gen.throwException()

      gen.visitLabel(endLabel)
    } else {
      // Try-catch-finally: combine both patterns
      val tryStart = gen.mark()
      visitStatement(node.tryStatement)
      val tryEnd = gen.mark()

      // Normal completion: execute finally and jump to end
      visitStatement(node.finallyStatement)
      val endLabel = gen.newLabel()
      gen.goTo(endLabel)

      // Catch handlers for specific exceptions
      for i <- node.catchTypes.indices do
        val catchType = node.catchTypes(i)
        val catchStmt = node.catchStatements(i)
        gen.catchException(tryStart, tryEnd, asmType(catchType.tp))
        val slot = gen.newLocal(asmType(catchType.tp))
        gen.storeLocal(slot)
        visitStatement(catchStmt)
        visitStatement(node.finallyStatement)  // Execute finally after catch
        gen.goTo(endLabel)

      // Catch-all handler for uncaught exceptions (finally + rethrow)
      gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
      val exSlot = gen.newLocal(AsmType.getType(classOf[Throwable]))
      gen.storeLocal(exSlot)
      visitStatement(node.finallyStatement)
      gen.loadLocal(exSlot)
      gen.throwException()

      gen.visitLabel(endLabel)
    }
}
