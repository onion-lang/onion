package onion.compiler

import org.objectweb.asm.{Label, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method => AsmMethod}
import onion.compiler.bytecode.{AsmUtil, ControlFlowEmitter, LocalVarContext, LoopContext, TermEmitter}
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
  private val controlFlow = new ControlFlowEmitter(gen, loops, localVars, asmType, visitTerm, visitStatement)
  private val termEmitter = new TermEmitter(gen, asmType, visitTerm)

  // Track last emitted line to avoid duplicate visitLineNumber calls
  private var lastEmittedLine = -1

  // Helper method to convert TypedAST types to ASM types
  private def asmType(tp: TypedAST.Type): AsmType = asmCodeGen.asmType(tp)

  // Emit line number if location exists and line changed
  private def emitLineNumber(location: Location): Unit =
    location match
      case null => // No location info
      case loc if loc.line != lastEmittedLine =>
        val label = gen.mark()
        gen.visitLineNumber(loc.line, label)
        lastEmittedLine = loc.line
      case _ => // Same line, skip
  
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
          case t if t.isBottomType => // Nothing to pop
          case BasicType.VOID => // Nothing to pop
          case BasicType.LONG | BasicType.DOUBLE => gen.pop2()
          case _ => gen.pop()
  
  override def visitBinaryTerm(node: BinaryTerm): Unit =
    termEmitter.emitBinaryTerm(node)
  
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
    val expectedArgs = node.method.arguments.map(asmType)
    var i = 0
    while i < node.parameters.length do
      val param = node.parameters(i)
      visitTerm(param)
      asmCodeGen.adaptValueOnStack(gen, param.`type`, expectedArgs(i))
      i += 1
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
    val expectedArgs = node.method.arguments.map(asmType)
    var i = 0
    while i < node.parameters.length do
      val param = node.parameters(i)
      visitTerm(param)
      asmCodeGen.adaptValueOnStack(gen, param.`type`, expectedArgs(i))
      i += 1
    val ownerType = AsmUtil.objectType(node.target.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      node.method.arguments.map(asmType)*
    )
    gen.invokeStatic(ownerType, AsmMethod(node.method.name, methodDesc))
  
  override def visitCallSuper(node: CallSuper): Unit =
    visitTerm(node.target)
    val expectedArgs = node.method.arguments.map(asmType)
    var i = 0
    while i < node.params.length do
      val param = node.params(i)
      visitTerm(param)
      asmCodeGen.adaptValueOnStack(gen, param.`type`, expectedArgs(i))
      i += 1
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
        gen.unbox(asmType(to))
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
    asmCodeGen.adaptValueOnStack(gen, node.value.`type`, valueType)
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
    val argTypes = node.constructor.getArgs.map(asmType)
    var i = 0
    while i < node.parameters.length do
      val param = node.parameters(i)
      visitTerm(param)
      asmCodeGen.adaptValueOnStack(gen, param.`type`, argTypes(i))
      i += 1
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
    val fieldType = asmType(node.field.`type`)
    visitTerm(node.value)
    asmCodeGen.adaptValueOnStack(gen, node.value.`type`, fieldType)
    if fieldType.getSize() == 2 then gen.dup2() else gen.dup()
    val ownerType = AsmUtil.objectType(node.target.name)
    gen.putStatic(ownerType, node.field.name, fieldType)
  
  override def visitOuterThis(node: OuterThis): Unit =
    gen.loadThis()
    gen.getField(
      AsmUtil.objectType(className),
      "this$0",
      asmType(node.`type`)
    )
  
  override def visitThis(node: This): Unit = gen.loadThis()
  
  override def visitUnaryTerm(node: UnaryTerm): Unit =
    termEmitter.emitUnaryTerm(node)

  override def visitStatementTerm(node: StatementTerm): Unit =
    visitStatement(node.statement)

  override def visitSynchronizedTerm(node: SynchronizedTerm): Unit =
    emitLineNumber(node.location)
    controlFlow.emitSynchronizedTerm(node)

  // Statement visitors
  override def visitStatementBlock(node: StatementBlock): Unit =
    controlFlow.emitStatementBlock(node)
  
  override def visitBreak(node: Break): Unit =
    controlFlow.emitBreak(node)
  
  override def visitContinue(node: Continue): Unit =
    controlFlow.emitContinue(node)
  
  override def visitExpressionActionStatement(node: ExpressionActionStatement): Unit =
    emitLineNumber(node.location)
    controlFlow.emitExpressionActionStatement(node)

  override def visitIfStatement(node: IfStatement): Unit =
    emitLineNumber(node.location)
    controlFlow.emitIfStatement(node)

  override def visitConditionalLoop(node: ConditionalLoop): Unit =
    emitLineNumber(node.location)
    controlFlow.emitConditionalLoop(node)

  override def visitNOP(node: NOP): Unit = ()

  override def visitReturn(node: Return): Unit =
    emitLineNumber(node.location)
    controlFlow.emitReturn(node)

  override def visitSynchronized(node: Synchronized): Unit =
    emitLineNumber(node.location)
    controlFlow.emitSynchronized(node)

  override def visitThrow(node: Throw): Unit =
    emitLineNumber(node.location)
    controlFlow.emitThrow(node)

  override def visitTry(node: Try): Unit =
    emitLineNumber(node.location)
    controlFlow.emitTry(node)
