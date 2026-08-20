package onion.compiler.backend.asm

import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.TypedAST.UnaryTerm.Kind.*
import org.objectweb.asm.{Opcodes, Type => AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

final class TermEmitter(
  gen: GeneratorAdapter,
  asmType: Type => AsmType,
  visitTerm: Term => Unit
) {
  // Push lhs then rhs for an arithmetic/bitwise/comparison operator. If rhs
  // may run its own try/catch, lhs can't stay on the operand stack across it:
  // the JVM clears the stack when dispatching to an exception handler, so an
  // already-pushed lhs would be silently discarded on the exceptional path,
  // desyncing the merged stack shape from the normal path (the binary-operator
  // sibling of issue #669/#745/#747/#750). Spill it into a local instead and
  // reload it once rhs has settled -- mirroring
  // AsmCodeGenerationVisitor.visitSetField/visitSetArray. LOGICAL_AND/OR and
  // ELVIS don't need this: their short-circuit branching already consumes lhs
  // off the stack before rhs runs.
  private def pushOperands(node: BinaryTerm): Unit =
    if TermContainsTry.contains(node.rhs) then
      visitTerm(node.lhs)
      val lhsSlot = gen.newLocal(asmType(node.lhs.`type`))
      gen.storeLocal(lhsSlot)
      visitTerm(node.rhs)
      val rhsSlot = gen.newLocal(asmType(node.rhs.`type`))
      gen.storeLocal(rhsSlot)
      gen.loadLocal(lhsSlot)
      gen.loadLocal(rhsSlot)
    else
      visitTerm(node.lhs)
      visitTerm(node.rhs)

  def emitBinaryTerm(node: BinaryTerm): Unit =
    node.kind match
      case ADD =>
        pushOperands(node)
        gen.math(GeneratorAdapter.ADD, asmType(node.`type`))

      case SUBTRACT =>
        pushOperands(node)
        gen.math(GeneratorAdapter.SUB, asmType(node.`type`))

      case MULTIPLY =>
        pushOperands(node)
        gen.math(GeneratorAdapter.MUL, asmType(node.`type`))

      case DIVIDE =>
        pushOperands(node)
        gen.math(GeneratorAdapter.DIV, asmType(node.`type`))

      case MOD =>
        pushOperands(node)
        gen.math(GeneratorAdapter.REM, asmType(node.`type`))

      case LOGICAL_AND =>
        val falseLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        visitTerm(node.lhs)
        gen.visitJumpInsn(Opcodes.IFEQ, falseLabel)
        visitTerm(node.rhs)
        gen.visitJumpInsn(Opcodes.GOTO, endLabel)
        gen.visitLabel(falseLabel)
        gen.push(false)
        gen.visitLabel(endLabel)

      case LOGICAL_OR =>
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        visitTerm(node.lhs)
        gen.visitJumpInsn(Opcodes.IFNE, trueLabel)
        visitTerm(node.rhs)
        gen.visitJumpInsn(Opcodes.GOTO, endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)

      case BIT_AND =>
        pushOperands(node)
        gen.math(GeneratorAdapter.AND, asmType(node.`type`))

      case BIT_OR =>
        pushOperands(node)
        gen.math(GeneratorAdapter.OR, asmType(node.`type`))

      case XOR =>
        pushOperands(node)
        gen.math(GeneratorAdapter.XOR, asmType(node.`type`))

      case BIT_SHIFT_L2 =>
        pushOperands(node)
        gen.math(GeneratorAdapter.SHL, asmType(node.lhs.`type`))

      case BIT_SHIFT_R2 =>
        pushOperands(node)
        gen.math(GeneratorAdapter.SHR, asmType(node.lhs.`type`))

      case BIT_SHIFT_R3 =>
        pushOperands(node)
        gen.math(GeneratorAdapter.USHR, asmType(node.lhs.`type`))

      case LESS_THAN =>
        emitComparison(node, GeneratorAdapter.LT)

      case GREATER_THAN =>
        emitComparison(node, GeneratorAdapter.GT)

      case LESS_OR_EQUAL =>
        emitComparison(node, GeneratorAdapter.LE)

      case GREATER_OR_EQUAL =>
        emitComparison(node, GeneratorAdapter.GE)

      case EQUAL =>
        emitComparison(node, GeneratorAdapter.EQ)

      case NOT_EQUAL =>
        emitComparison(node, GeneratorAdapter.NE)

      case ELVIS =>
        val endLabel = gen.newLabel()
        visitTerm(node.lhs)
        gen.dup()
        gen.visitJumpInsn(Opcodes.IFNONNULL, endLabel)
        gen.pop()
        visitTerm(node.rhs)
        gen.visitLabel(endLabel)

  def emitUnaryTerm(node: UnaryTerm): Unit =
    node.kind match
      case PLUS => visitTerm(node.operand)

      case MINUS =>
        visitTerm(node.operand)
        gen.math(GeneratorAdapter.NEG, asmType(node.`type`))

      case NOT =>
        visitTerm(node.operand)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.visitJumpInsn(Opcodes.IFEQ, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)

      case BIT_NOT =>
        visitTerm(node.operand)
        node.`type` match
          case BasicType.INT =>
            gen.push(-1)
            gen.math(GeneratorAdapter.XOR, AsmType.INT_TYPE)
          case BasicType.LONG =>
            gen.push(-1L)
            gen.math(GeneratorAdapter.XOR, AsmType.LONG_TYPE)
          case _ =>
            throw new UnsupportedOperationException(s"Bitwise NOT not supported for type: ${node.`type`}")

  private def emitComparison(node: BinaryTerm, opcode: Int): Unit =
    pushOperands(node)
    val trueLabel = gen.newLabel()
    val endLabel = gen.newLabel()
    gen.ifCmp(asmType(node.lhs.`type`), opcode, trueLabel)
    gen.push(false)
    gen.goTo(endLabel)
    gen.visitLabel(trueLabel)
    gen.push(true)
    gen.visitLabel(endLabel)
}
