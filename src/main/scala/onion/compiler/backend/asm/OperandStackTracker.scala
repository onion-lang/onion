package onion.compiler.backend.asm

import org.objectweb.asm.{Handle, Label, MethodVisitor, Opcodes}
import org.objectweb.asm.commons.AnalyzerAdapter

import java.util.{ArrayList as JArrayList, List as JList}
import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * Follows the JVM operand stack while a method is emitted, so a `break`/`continue`
 * can drop what an enclosing expression has already pushed before it jumps (#2932).
 *
 * In `10 + (if c { continue } else { i })` the `10` is on the stack when the jump
 * runs, and the loop head expects an empty one, so the frames cannot be merged.
 *
 * It is `AnalyzerAdapter` plus one thing that class lacks: after an unconditional
 * jump the state is unknown, and `AnalyzerAdapter` only learns it again from a
 * visited frame. Codegen visits none (the class writer computes frames), so this
 * class remembers the state at every jump to a label and restores it there; a
 * handler gets its try-start locals and the caught exception.
 *
 * It only observes: an instruction it cannot follow makes the state unknown, and an
 * unknown state means "emit the jump as before".
 */
final class OperandStackTracker(access: Int, name: String, descriptor: String)
    extends AnalyzerAdapter(Opcodes.ASM9, "java/lang/Object", access, name, descriptor, null) {

  private final case class State(locals: JList[Object], stack: JList[Object])

  private val atLabel = mutable.HashMap[Label, State]()
  private val localsAtLabel = mutable.HashMap[Label, JList[Object]]()
  private var broken = false

  private def snapshot(dropTop: Int = 0): State =
    val st = new JArrayList[Object](stack)
    var n = dropTop
    while n > 0 && !st.isEmpty do
      st.remove(st.size - 1)
      n -= 1
    State(new JArrayList[Object](locals), st)

  private def known: Boolean = !broken && stack != null && locals != null

  private def record(label: Label, state: State): Unit =
    if !atLabel.contains(label) then atLabel(label) = state

  override def visitJumpInsn(opcode: Int, label: Label): Unit =
    if known && opcode == Opcodes.GOTO then record(label, snapshot())
    super.visitJumpInsn(opcode, label)
    if known && opcode != Opcodes.GOTO && opcode != Opcodes.JSR then record(label, snapshot())

  override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: Label*): Unit =
    if known then
      val s = snapshot(dropTop = 1)
      (dflt +: labels).foreach(record(_, s))
    super.visitTableSwitchInsn(min, max, dflt, labels*)

  override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], labels: Array[Label]): Unit =
    if known then
      val s = snapshot(dropTop = 1)
      (dflt +: labels.toSeq).foreach(record(_, s))
    super.visitLookupSwitchInsn(dflt, keys, labels)

  override def visitTryCatchBlock(start: Label, end: Label, handler: Label, tpe: String): Unit =
    super.visitTryCatchBlock(start, end, handler, tpe)
    localsAtLabel.get(start).foreach { ls =>
      val st = new JArrayList[Object]()
      st.add(if tpe == null then "java/lang/Throwable" else tpe)
      record(handler, State(new JArrayList[Object](ls), st))
    }

  override def visitLabel(label: Label): Unit =
    super.visitLabel(label)
    if !broken then
      if stack == null || locals == null then
        atLabel.get(label).foreach { s =>
          locals = new JArrayList[Object](s.locals)
          stack = new JArrayList[Object](s.stack)
        }
      if locals != null then localsAtLabel(label) = new JArrayList[Object](locals)

  /** Stop following the method: every later question gets "unknown". */
  def markBroken(): Unit = broken = true

  /** The stack's size in entries (a long or double takes two), or -1 when unknown. */
  def depth: Int = if known then stack.size else -1

  /**
   * The pops (`Opcodes.POP` or `Opcodes.POP2`) that bring the stack down to `target`
   * entries, top first; `None` when the stack is unknown or already at or below it.
   */
  def popsDownTo(target: Int): Option[List[Int]] =
    if !known || target < 0 || stack.size <= target then None
    else
      val buf = mutable.ListBuffer[Int]()
      var i = stack.size
      var ok = true
      while ok && i > target do
        val top = stack.get(i - 1)
        val below = if i >= 2 then stack.get(i - 2) else null
        if top == Opcodes.TOP && (below == Opcodes.LONG || below == Opcodes.DOUBLE) then
          if i - 2 < target then ok = false
          else
            buf += Opcodes.POP2
            i -= 2
        else
          buf += Opcodes.POP
          i -= 1
      if ok then Some(buf.toList) else None
}

/**
 * Sends every instruction to the class writer's method visitor and a copy to an
 * [[OperandStackTracker]]. A failure inside the tracker only stops the tracking;
 * the emitted code never depends on it.
 */
final class TrackingMethodVisitor(primary: MethodVisitor, val tracker: OperandStackTracker)
    extends MethodVisitor(Opcodes.ASM9, primary) {

  private def track(f: => Unit): Unit =
    try f
    catch case NonFatal(_) => tracker.markBroken()

  override def visitInsn(opcode: Int): Unit =
    super.visitInsn(opcode); track(tracker.visitInsn(opcode))
  override def visitIntInsn(opcode: Int, operand: Int): Unit =
    super.visitIntInsn(opcode, operand); track(tracker.visitIntInsn(opcode, operand))
  override def visitVarInsn(opcode: Int, varIndex: Int): Unit =
    super.visitVarInsn(opcode, varIndex); track(tracker.visitVarInsn(opcode, varIndex))
  override def visitTypeInsn(opcode: Int, tpe: String): Unit =
    super.visitTypeInsn(opcode, tpe); track(tracker.visitTypeInsn(opcode, tpe))
  override def visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String): Unit =
    super.visitFieldInsn(opcode, owner, name, descriptor)
    track(tracker.visitFieldInsn(opcode, owner, name, descriptor))
  override def visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean): Unit =
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    track(tracker.visitMethodInsn(opcode, owner, name, descriptor, isInterface))
  override def visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, bsmArgs: Object*): Unit =
    super.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs*)
    track(tracker.visitInvokeDynamicInsn(name, descriptor, bsm, bsmArgs*))
  override def visitJumpInsn(opcode: Int, label: Label): Unit =
    super.visitJumpInsn(opcode, label); track(tracker.visitJumpInsn(opcode, label))
  override def visitLabel(label: Label): Unit =
    super.visitLabel(label); track(tracker.visitLabel(label))
  override def visitLdcInsn(value: Object): Unit =
    super.visitLdcInsn(value); track(tracker.visitLdcInsn(value))
  override def visitIincInsn(varIndex: Int, increment: Int): Unit =
    super.visitIincInsn(varIndex, increment); track(tracker.visitIincInsn(varIndex, increment))
  override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: Label*): Unit =
    super.visitTableSwitchInsn(min, max, dflt, labels*)
    track(tracker.visitTableSwitchInsn(min, max, dflt, labels*))
  override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], labels: Array[Label]): Unit =
    super.visitLookupSwitchInsn(dflt, keys, labels); track(tracker.visitLookupSwitchInsn(dflt, keys, labels))
  override def visitMultiANewArrayInsn(descriptor: String, numDimensions: Int): Unit =
    super.visitMultiANewArrayInsn(descriptor, numDimensions)
    track(tracker.visitMultiANewArrayInsn(descriptor, numDimensions))
  override def visitTryCatchBlock(start: Label, end: Label, handler: Label, tpe: String): Unit =
    super.visitTryCatchBlock(start, end, handler, tpe); track(tracker.visitTryCatchBlock(start, end, handler, tpe))
  override def visitFrame(tpe: Int, numLocal: Int, local: Array[Object], numStack: Int, stack: Array[Object]): Unit =
    super.visitFrame(tpe, numLocal, local, numStack, stack); tracker.markBroken()
}
