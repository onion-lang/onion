package onion.compiler.backend.asm

import onion.compiler.LocalBinding
import org.objectweb.asm.{Type as AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

import scala.collection.mutable

/** One LocalVariableTable row: the JVM slot, the author's name for it, and its type. */
final case class DebugLocal(slot: Int, name: String, descriptor: String)

class LocalVarContext(gen: GeneratorAdapter) {
  // Typed-AST index -> JVM slot, and two index sets, as flat structures: a boxed
  // `Map[Int, Int]` and two `Set[Int]`s were consulted for every local read and write.
  private var slots: Array[Int] = new Array[Int](16)
  java.util.Arrays.fill(slots, -1)
  private val parameterSet = new java.util.BitSet()
  private val boxedSet = new java.util.BitSet()
  private def setSlot(typedIndex: Int, slot: Int): Unit = {
    if (typedIndex >= slots.length) {
      val grown = java.util.Arrays.copyOf(slots, math.max(slots.length * 2, typedIndex + 1))
      java.util.Arrays.fill(grown, slots.length, grown.length, -1)
      slots = grown
    }
    slots(typedIndex) = slot
  }
  private def slotOrMinusOne(typedIndex: Int): Int =
    if (typedIndex >= 0 && typedIndex < slots.length) slots(typedIndex) else -1

  // Debug info. Names come from the frame, because `LocalBinding` does not carry one and
  // the scopes are the only thing that still knows what the author called a variable by
  // the time codegen runs. A slot with no name is simply left out of the table rather
  // than invented — a debugger showing `var3` is worse than showing nothing.
  private var nameFrame: onion.compiler.LocalFrame = null
  private var namesCache: Array[String] = null
  // Built on the first slot that needs a name: a method with no locals never pays for it.
  private def names: Array[String] = {
    if (namesCache == null) namesCache = if (nameFrame == null) Array.empty else nameFrame.namesArray
    namesCache
  }
  private val debugLocals = mutable.Buffer[DebugLocal]()

  /** Must be called before slots are allocated, or those allocations go unnamed. */
  def withNames(frame: onion.compiler.LocalFrame): LocalVarContext = {
    if (frame != null) { nameFrame = frame; namesCache = null }
    this
  }

  def declaredLocals: Seq[DebugLocal] = debugLocals.toSeq

  private def recordDebug(typedIndex: Int, slot: Int, tp: AsmType): Unit =
    val table = names
    val name = if (typedIndex >= 0 && typedIndex < table.length) table(typedIndex) else null
    Option(name).foreach { name =>
      debugLocals += DebugLocal(slot, name, tp.getDescriptor)
    }

  def slotOf(typedIndex: Int): Option[Int] = {
    val slot = slotOrMinusOne(typedIndex)
    if (slot < 0) None else Some(slot)
  }

  def getOrAllocateSlot(typedIndex: Int, tp: AsmType): Int = {
    val existing = slotOrMinusOne(typedIndex)
    if (existing >= 0) existing
    else {
      val slot = gen.newLocal(tp)
      setSlot(typedIndex, slot)
      recordDebug(typedIndex, slot, tp)
      slot
    }
  }

  def allocateSlot(typedIndex: Int, tp: AsmType): Int = {
    val slot = gen.newLocal(tp)
    setSlot(typedIndex, slot)
    recordDebug(typedIndex, slot, tp)
    slot
  }

  def isParameter(typedIndex: Int): Boolean = typedIndex >= 0 && parameterSet.get(typedIndex)

  def isBoxed(typedIndex: Int): Boolean = typedIndex >= 0 && boxedSet.get(typedIndex)

  def markAsBoxed(typedIndex: Int): Unit = boxedSet.set(typedIndex)

  /**
    * Register JVM parameter slots. Slot0 is `this` for instance methods.
    */
  def withParameters(isStatic: Boolean, argTypes: Array[AsmType]): LocalVarContext = {
    var slot = if isStatic then 0 else 1
    var i = 0
    while (i < argTypes.length) {
      val tp = argTypes(i)
      setSlot(i, slot)
      parameterSet.set(i)
      recordDebug(i, slot, tp)
      slot += tp.getSize
      i += 1
    }
    this
  }

  /** Records slot 0 of an instance method, which no frame knows about. */
  def withThis(ownerDescriptor: String): LocalVarContext = {
    debugLocals += DebugLocal(0, "this", ownerDescriptor)
    this
  }

  /**
    * Mark variables as boxed based on LocalFrame information.
    */
  def withBoxedVariables(frame: onion.compiler.LocalFrame): LocalVarContext = {
    if (frame != null) {
      // Order is irrelevant here; `entries` sorted a fresh array per method.
      for (binding <- frame.allBindings) {
        if (binding.isBoxed) {
          boxedSet.set(binding.index)
        }
      }
    }
    this
  }
}

class ClosureLocalVarContext(
  gen: GeneratorAdapter,
  val closureClassName: String,
  val capturedVars: Seq[onion.compiler.ClosureLocalBinding]
) extends LocalVarContext(gen) {
  // A closure captures a handful of variables; a linear scan over an array beats a map
  // keyed by a (frame, index) tuple that had to be allocated for every local read.
  private val capturedArray: Array[onion.compiler.ClosureLocalBinding] = capturedVars.toArray
  private def find(frameIndex: Int, typedIndex: Int): onion.compiler.ClosureLocalBinding = {
    var i = 0
    while (i < capturedArray.length) {
      val b = capturedArray(i)
      if (b.frameIndex == frameIndex && b.index == typedIndex) return b
      i += 1
    }
    null
  }

  def capturedFieldName(frameIndex: Int, typedIndex: Int): String = s"captured_${frameIndex}_$typedIndex"

  def capturedFieldName(binding: onion.compiler.ClosureLocalBinding): String =
    capturedFieldName(binding.frameIndex, binding.index)

  def capturedBinding(frameIndex: Int, typedIndex: Int): Option[onion.compiler.ClosureLocalBinding] =
    Option(find(frameIndex, typedIndex))

  def capturedBinding(typedIndex: Int): Option[onion.compiler.ClosureLocalBinding] = Option(find(0, typedIndex))

  def isCapturedVariable(frameIndex: Int, typedIndex: Int): Boolean = find(frameIndex, typedIndex) != null

  def isCapturedVariable(typedIndex: Int): Boolean = find(0, typedIndex) != null

  override def getOrAllocateSlot(typedIndex: Int, tp: AsmType): Int =
    capturedBinding(typedIndex) match {
      case Some(b) => throw new IllegalStateException(s"Attempted to allocate slot for captured variable ${b.index}")
      case None    => super.getOrAllocateSlot(typedIndex, tp)
    }
}
