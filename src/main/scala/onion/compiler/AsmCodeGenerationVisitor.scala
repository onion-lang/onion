package onion.compiler

import org.objectweb.asm.{Label, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method => AsmMethod}
import onion.compiler.bytecode.{AsmUtil, LocalVarContext, LoopContext}
import TypedAST._

/**
 * Visitor implementation for ASM bytecode generation from TypedAST nodes.
 * This visitor encapsulates the bytecode generation logic previously scattered
 * throughout AsmCodeGeneration.scala.
 */
class AsmCodeGenerationVisitor(
  gen: GeneratorAdapter,
  className: String,
  localVars: LocalVarContext,
  asmCodeGen: AsmCodeGeneration
) extends TypedASTVisitor[Unit]:
  
  private val loops = new LoopContext

  import BinaryTerm.Constants._
  import UnaryTerm.Constants._
  
  // Helper method to convert TypedAST types to ASM types
  private def asmType(tp: TypedAST.Type): AsmType = asmCodeGen.asmType(tp)
  
  // Expression visitors
  override def visitArrayLength(node: ArrayLength): Unit =
    visitTerm(node.target)
    gen.arrayLength()
  
  override def visitRefArray(node: RefArray): Unit =
    visitTerm(node.target)
    visitTerm(node.index)
    gen.arrayLoad(asmType(node.`type`))
  
  override def visitSetArray(node: SetArray): Unit =
    visitTerm(node.target)
    visitTerm(node.index)
    visitTerm(node.value)
    // Duplicate value for return
    val valueType = asmType(node.`type`)
    if valueType.getSize() == 2 then
      gen.dup2X2()
    else
      gen.dupX2()
    gen.arrayStore(valueType)
  
  override def visitBegin(node: Begin): Unit =
    for i <- node.terms.indices do
      visitTerm(node.terms(i))
      // Pop intermediate results except the last one
      if i < node.terms.length - 1 then
        node.terms(i).`type` match
          case BasicType.VOID => // Nothing to pop
          case BasicType.LONG | BasicType.DOUBLE => gen.pop2()
          case _ => gen.pop()
  
  override def visitBinaryTerm(node: BinaryTerm): Unit =
    node.kind match
      // Arithmetic operations
      case ADD =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.ADD, asmType(node.`type`))
      
      case SUBTRACT =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.SUB, asmType(node.`type`))
      
      case MULTIPLY =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.MUL, asmType(node.`type`))
      
      case DIVIDE =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.DIV, asmType(node.`type`))
      
      case MOD =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.REM, asmType(node.`type`))
      
      // Logical operations
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
      
      // Bitwise operations
      case BIT_AND =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.AND, asmType(node.`type`))
      
      case BIT_OR =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.OR, asmType(node.`type`))
      
      case XOR =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.XOR, asmType(node.`type`))
      
      // Shift operations
      case BIT_SHIFT_L2 =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.SHL, asmType(node.lhs.`type`))
      
      case BIT_SHIFT_R2 =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.SHR, asmType(node.lhs.`type`))
      
      case BIT_SHIFT_R3 =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        gen.math(GeneratorAdapter.USHR, asmType(node.lhs.`type`))
      
      // Comparison operations
      case LESS_THAN =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.ifCmp(asmType(node.lhs.`type`), GeneratorAdapter.LT, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)
      
      case GREATER_THAN =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.ifCmp(asmType(node.lhs.`type`), GeneratorAdapter.GT, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)
      
      case LESS_OR_EQUAL =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.ifCmp(asmType(node.lhs.`type`), GeneratorAdapter.LE, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)
      
      case GREATER_OR_EQUAL =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.ifCmp(asmType(node.lhs.`type`), GeneratorAdapter.GE, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)
      
      case EQUAL =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.ifCmp(asmType(node.lhs.`type`), GeneratorAdapter.EQ, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)
      
      case NOT_EQUAL =>
        visitTerm(node.lhs)
        visitTerm(node.rhs)
        val trueLabel = gen.newLabel()
        val endLabel = gen.newLabel()
        gen.ifCmp(asmType(node.lhs.`type`), GeneratorAdapter.NE, trueLabel)
        gen.push(false)
        gen.goTo(endLabel)
        gen.visitLabel(trueLabel)
        gen.push(true)
        gen.visitLabel(endLabel)
      
      case ELVIS =>
        val endLabel = gen.newLabel()
        visitTerm(node.lhs)
        gen.dup()
        gen.visitJumpInsn(Opcodes.IFNONNULL, endLabel)
        gen.pop()
        visitTerm(node.rhs)
        gen.visitLabel(endLabel)
  
  override def visitBoolValue(node: BoolValue): Unit = gen.push(node.value)
  override def visitByteValue(node: ByteValue): Unit = gen.push(node.value.toInt)
  override def visitCharacterValue(node: CharacterValue): Unit = gen.push(node.value.toInt)
  override def visitDoubleValue(node: DoubleValue): Unit = gen.push(node.value)
  override def visitFloatValue(node: FloatValue): Unit = gen.push(node.value)
  override def visitIntValue(node: IntValue): Unit = gen.push(node.value)
  override def visitLongValue(node: LongValue): Unit = gen.push(node.value)
  override def visitShortValue(node: ShortValue): Unit = gen.push(node.value.toInt)
  override def visitStringValue(node: StringValue): Unit = gen.push(node.value)
  override def visitNullValue(node: NullValue): Unit = gen.visitInsn(Opcodes.ACONST_NULL)
  
  override def visitCall(node: Call): Unit =
    visitTerm(node.target)
    for param <- node.parameters do
      visitTerm(param)
    val ownerType = AsmUtil.objectType(node.method.affiliation.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      node.method.arguments.map(asmType)*
    )
    val isInterface = node.method.affiliation.isInterface
    if isInterface then
      gen.invokeInterface(ownerType, AsmMethod(node.method.name, methodDesc))
    else
      gen.invokeVirtual(ownerType, AsmMethod(node.method.name, methodDesc))
  
  override def visitCallStatic(node: CallStatic): Unit =
    for param <- node.parameters do
      visitTerm(param)
    val ownerType = AsmUtil.objectType(node.target.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      node.method.arguments.map(asmType)*
    )
    gen.invokeStatic(ownerType, AsmMethod(node.method.name, methodDesc))
  
  override def visitCallSuper(node: CallSuper): Unit =
    visitTerm(node.target)
    for param <- node.params do
      visitTerm(param)
    val ownerType = AsmUtil.objectType(node.method.affiliation.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      node.method.arguments.map(asmType)*
    )
    gen.visitMethodInsn(
      Opcodes.INVOKESPECIAL,
      ownerType.getInternalName,
      node.method.name,
      methodDesc,
      false
    )
  
  override def visitAsInstanceOf(node: AsInstanceOf): Unit =
    visitTerm(node.target)
    (node.target.`type`, node.destination) match
      case (from: BasicType, to: BasicType) =>
        gen.cast(asmType(from), asmType(to))
      case (_: BasicType, _: ObjectType | _: ArrayType | _: NullType) =>
        throw new RuntimeException(s"Unsupported cast from basic to reference: ${node.target.`type`} => ${node.destination}")
      case (_: ObjectType | _: ArrayType | _: NullType, to: BasicType) =>
        throw new RuntimeException(s"Unsupported cast from reference to basic: ${node.target.`type`} => ${node.destination}")
      case _ =>
        gen.checkCast(asmType(node.destination))
  
  override def visitInstanceOf(node: InstanceOf): Unit =
    visitTerm(node.target)
    gen.instanceOf(asmType(node.checked))
  
  override def visitListLiteral(node: ListLiteral): Unit =
    // Create ArrayList
    gen.newInstance(AsmUtil.objectType("java.util.ArrayList"))
    gen.dup()
    gen.push(node.elements.length)
    gen.invokeConstructor(
      AsmUtil.objectType("java.util.ArrayList"),
      AsmMethod.getMethod("void <init>(int)")
    )
    
    // Add elements
    for elem <- node.elements do
      gen.dup() // Duplicate list reference
      visitTerm(elem)
      // Box primitive if needed
      elem.`type` match
        case bt: BasicType => gen.box(asmType(bt))
        case _ => // Already an object
      gen.invokeVirtual(
        AsmUtil.objectType("java.util.ArrayList"),
        AsmMethod.getMethod("boolean add(Object)")
      )
      gen.pop() // Pop boolean result
  
  override def visitRefLocal(node: RefLocal): Unit =
    asmCodeGen.emitRefLocal(gen, node, localVars)
  
  override def visitSetLocal(node: SetLocal): Unit =
    asmCodeGen.emitSetLocal(gen, node, className, localVars)
  
  override def visitNewClosure(node: NewClosure): Unit =
    asmCodeGen.emitNewClosure(gen, node, className, localVars)
  
  override def visitRefField(node: RefField): Unit =
    visitTerm(node.target)
    val ownerType = AsmUtil.objectType(node.field.affiliation.name)
    gen.getField(ownerType, node.field.name, asmType(node.field.`type`))
  
  override def visitSetField(node: SetField): Unit =
    visitTerm(node.target)
    visitTerm(node.value)
    // Duplicate value for return
    val valueType = asmType(node.`type`)
    if valueType.getSize() == 2 then
      gen.dup2X1()
    else
      gen.dupX1()
    val ownerType = AsmUtil.objectType(node.field.affiliation.name)
    gen.putField(ownerType, node.field.name, valueType)
  
  override def visitNewObject(node: NewObject): Unit =
    val classType = AsmUtil.objectType(node.constructor.affiliation.name)
    gen.newInstance(classType)
    gen.dup()
    for param <- node.parameters do
      visitTerm(param)
    val argTypes = node.constructor.getArgs.map(asmType)
    gen.invokeConstructor(classType, AsmMethod("<init>", AsmType.getMethodDescriptor(AsmType.VOID_TYPE, argTypes*)))
  
  override def visitNewArray(node: NewArray): Unit =
    for param <- node.parameters do
      visitTerm(param)
    val componentType = asmType(node.arrayType.component)
    if node.parameters.length == 1 then
      gen.newArray(componentType)
    else
      val dims = Array.fill(node.parameters.length)(AsmType.INT_TYPE)
      gen.visitMultiANewArrayInsn(asmType(node.arrayType).getDescriptor, node.parameters.length)
  
  override def visitRefStaticField(node: RefStaticField): Unit =
    val ownerType = AsmUtil.objectType(node.target.name)
    gen.getStatic(ownerType, node.field.name, asmType(node.field.`type`))
  
  override def visitSetStaticField(node: SetStaticField): Unit =
    visitTerm(node.value)
    gen.dup()
    val ownerType = AsmUtil.objectType(node.target.name)
    gen.putStatic(ownerType, node.field.name, asmType(node.field.`type`))
  
  override def visitOuterThis(node: OuterThis): Unit =
    gen.loadThis()
    gen.getField(
      AsmUtil.objectType(className),
      "this$0",
      asmType(node.`type`)
    )
  
  override def visitThis(node: This): Unit = gen.loadThis()
  
  override def visitUnaryTerm(node: UnaryTerm): Unit =
    node.kind match
      case PLUS => visitTerm(node.operand) // No-op
      
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
  
  // Statement visitors
  override def visitStatementBlock(node: StatementBlock): Unit =
    for stmt <- node.statements do
      visitStatement(stmt)
  
  override def visitBreak(node: Break): Unit =
    loops.currentEnd match
      case Some(label) => gen.goTo(label)
      case None => throw new RuntimeException("Break statement outside of loop")
  
  override def visitContinue(node: Continue): Unit =
    loops.currentStart match
      case Some(label) => gen.goTo(label)
      case None => throw new RuntimeException("Continue statement outside of loop")
  
  override def visitExpressionActionStatement(node: ExpressionActionStatement): Unit =
    visitTerm(node.term)
    // Pop the result if it's not void
    node.term.`type` match
      case BasicType.VOID => // Nothing to pop
      case BasicType.LONG | BasicType.DOUBLE => gen.pop2()
      case _ => gen.pop()
  
  override def visitIfStatement(node: IfStatement): Unit =
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
  
  override def visitConditionalLoop(node: ConditionalLoop): Unit =
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
  
  override def visitNOP(node: NOP): Unit = ()
  
  override def visitReturn(node: Return): Unit =
    if node.term != null then
      visitTerm(node.term)
    gen.returnValue()
  
  override def visitSynchronized(node: Synchronized): Unit =
    visitTerm(node.term)
    gen.monitorEnter()
    
    val tryStart = gen.mark()
    visitStatement(node.statement)
    val tryEnd = gen.mark()
    
    visitTerm(node.term)
    gen.monitorExit()
    val endLabel = gen.newLabel()
    gen.goTo(endLabel)
    
    // Exception handler to ensure monitor exit
    val handlerStart = gen.mark()
    visitTerm(node.term)
    gen.monitorExit()
    gen.throwException()
    
    gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
    gen.visitLabel(endLabel)
  
  override def visitThrow(node: Throw): Unit =
    visitTerm(node.term)
    gen.throwException()
  
  override def visitTry(node: Try): Unit =
    val tryStart = gen.mark()
    visitStatement(node.tryStatement)
    val tryEnd = gen.mark()
    
    // Jump to end if no exception
    val endLabel = gen.newLabel()
    gen.goTo(endLabel)
    
    // Exception handlers
    for i <- node.catchTypes.indices do
      val catchType = node.catchTypes(i)
      val catchStmt = node.catchStatements(i)
      val handlerStart = gen.mark()
      // Store exception in local variable
      val slot = gen.newLocal(asmType(catchType.tp))
      gen.storeLocal(slot)
      visitStatement(catchStmt)
      gen.catchException(tryStart, tryEnd, asmType(catchType.tp))
    
    gen.visitLabel(endLabel)
