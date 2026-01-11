package onion.compiler.bytecode

import onion.compiler.LocalBinding
import org.objectweb.asm.{Type as AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

import scala.collection.mutable

class LocalVarContext(gen: GeneratorAdapter) {
  private val indexMap = mutable.Map[Int, Int]()
  private val parameterSet = mutable.Set[Int]()
  private val boxedSet = mutable.Set[Int]()

  def slotOf(typedIndex: Int): Option[Int] = indexMap.get(typedIndex)

  def getOrAllocateSlot(typedIndex: Int, tp: AsmType): Int =
    indexMap.getOrElseUpdate(typedIndex, gen.newLocal(tp))

  def allocateSlot(typedIndex: Int, tp: AsmType): Int = {
    val slot = gen.newLocal(tp)
    indexMap(typedIndex) = slot
    slot
  }

  def isParameter(typedIndex: Int): Boolean = parameterSet.contains(typedIndex)

  def isBoxed(typedIndex: Int): Boolean = boxedSet.contains(typedIndex)

  def markAsBoxed(typedIndex: Int): Unit = boxedSet += typedIndex

  /**
    * Register JVM parameter slots. Slot0 is `this` for instance methods.
    */
  def withParameters(isStatic: Boolean, argTypes: Array[AsmType]): LocalVarContext = {
    val startSlot = if isStatic then 0 else 1
    argTypes.zipWithIndex.foldLeft(startSlot) { case (slot, (tp, i)) =>
      indexMap(i) = slot
      parameterSet += i
      slot + tp.getSize
    }
    this
  }

  /**
    * Mark variables as boxed based on LocalFrame information.
    */
  def withBoxedVariables(frame: onion.compiler.LocalFrame): LocalVarContext = {
    if (frame != null) {
      for (binding <- frame.entries) {
        if (binding.isBoxed) {
          boxedSet += binding.index
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
  private val capturedByIndex: Map[Int, onion.compiler.ClosureLocalBinding] =
    capturedVars.map(b => b.index -> b).toMap

  def capturedFieldName(typedIndex: Int): String = s"captured_$typedIndex"

  def capturedBinding(typedIndex: Int): Option[onion.compiler.ClosureLocalBinding] = capturedByIndex.get(typedIndex)

  def isCapturedVariable(typedIndex: Int): Boolean = capturedByIndex.contains(typedIndex)

  override def getOrAllocateSlot(typedIndex: Int, tp: AsmType): Int =
    capturedBinding(typedIndex) match {
      case Some(b) => throw new IllegalStateException(s"Attempted to allocate slot for captured variable ${b.index}")
      case None    => super.getOrAllocateSlot(typedIndex, tp)
    }
}

