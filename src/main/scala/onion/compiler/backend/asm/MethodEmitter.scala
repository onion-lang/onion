package onion.compiler.backend.asm

import org.objectweb.asm.{ClassWriter, Label, MethodVisitor, Opcodes, Type as AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method as AsmMethod}

/**
 * A `GeneratorAdapter` that can write a LocalVariableTable entry without the slot being
 * remapped a second time.
 *
 * `GeneratorAdapter` extends `LocalVariablesSorter`, whose `visitLocalVariable` remaps the
 * index it is given from the *old* numbering into the new one. The slots recorded during
 * codegen come back from `newLocal`, so they are already in the new numbering; handing
 * them to the sorter would map them again and describe the wrong slot. Writing straight to
 * the underlying visitor is the only way to say "this slot, exactly".
 *
 * Parameters would survive the remapping untouched, but they go through the same door so
 * there is one rule rather than two.
 */
final class DebugGeneratorAdapter(access: Int, method: AsmMethod, methodVisitor: MethodVisitor)
    // The (access, Method, MethodVisitor) constructor refuses to be used by a subclass --
    // it throws IllegalStateException when `getClass() != GeneratorAdapter.class`. The
    // protected api-taking one is the entry point ASM intends for subclasses.
    extends GeneratorAdapter(
      Opcodes.ASM9, methodVisitor, access, method.getName, method.getDescriptor) {

  def emitLocalVariableEntry(
    name: String,
    descriptor: String,
    start: Label,
    end: Label,
    slot: Int
  ): Unit =
    if (mv != null) mv.visitLocalVariable(name, descriptor, null, start, end, slot)

  /** Follows the operand stack for `break`/`continue` (#2932); null when not tracked. */
  var stackTracker: OperandStackTracker = null

  // Where each line-number entry so far starts, so a caller can avoid a second entry at the
  // same instruction: the JVM then reports the first entry for an exception thrown exactly
  // there, and the last for one thrown later, so neither order gives the right line.
  private val lineStarts = scala.collection.mutable.ArrayBuffer[Label]()

  override def visitLineNumber(line: Int, start: Label): Unit =
    lineStarts += start
    super.visitLineNumber(line, start)

  /** Whether a line-number entry already starts at `label`'s instruction. */
  def hasLineNumberAt(label: Label): Boolean = {
    def offset(l: Label): Int = try l.getOffset catch { case _: IllegalStateException => -1 }
    val at = offset(label)
    at >= 0 && lineStarts.exists(offset(_) == at)
  }
}

object MethodEmitter {
  def newGenerator(
    cw: ClassWriter,
    access: Int,
    name: String,
    returnType: AsmType,
    argTypes: Array[AsmType],
    exceptions: Array[String] = null,
    signature: String = null
  ): DebugGeneratorAdapter = {
    val desc = AsmType.getMethodDescriptor(returnType, argTypes*)
    val tracker = new OperandStackTracker(access, name, desc)
    val mv = new TrackingMethodVisitor(cw.visitMethod(access, name, desc, signature, exceptions), tracker)
    val asmMethod = AsmMethod(name, desc)
    val gen = new DebugGeneratorAdapter(access, asmMethod, mv)
    gen.stackTracker = tracker
    gen.visitCode()
    gen
  }

  def ensureReturn(gen: GeneratorAdapter, returnType: AsmType, hasReturn: Boolean): Unit = {
    if (!hasReturn) AsmUtil.emitDefaultReturn(gen, returnType)
  }
}
