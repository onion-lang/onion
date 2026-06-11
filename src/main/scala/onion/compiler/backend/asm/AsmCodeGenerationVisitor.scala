package onion.compiler.backend.asm

import org.objectweb.asm.{Label, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method => AsmMethod}
import onion.compiler.{Location, Modifier, TypedAST, TypedASTVisitor}
import TypedAST.*

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

  // Emit method arguments with boxing/unboxing adaptation
  private def emitArgumentsWithAdaptation(
    params: Array[Term],
    expectedTypes: Array[AsmType]
  ): Unit =
    var i = 0
    while i < params.length do
      val param = params(i)
      visitTerm(param)
      asmCodeGen.adaptValueOnStack(gen, param.`type`, expectedTypes(i))
      i += 1

  // Expression visitors
  override def visitArrayLength(node: ArrayLength): Unit =
    visitTerm(node.target)
    gen.arrayLength()
  
  override def visitRefArray(node: RefArray): Unit =
    visitTerm(node.target)
    visitTerm(node.index)
    gen.arrayLoad(asmType(node.`type`))

  /**
   * Non-null assertion: target!! — throw NullPointerException on null,
   * otherwise leave the value on the stack.
   */
  override def visitNonNullAssert(node: NonNullAssert): Unit =
    val okLabel = gen.newLabel()
    visitTerm(node.target)
    gen.dup()
    gen.visitJumpInsn(Opcodes.IFNONNULL, okLabel)
    gen.throwException(
      AsmType.getObjectType("java/lang/NullPointerException"),
      "expression evaluated to null (!! assertion failed)"
    )
    gen.visitLabel(okLabel)

  /**
   * Safe array indexing: target?[index]
   *   target            // stack: [target]
   *   dup, ifnull L     // null check
   *   index, arrayload  // stack: [element]
   *   box if primitive  // result is nullable
   *   goto end
   *   L: pop, aconst_null
   *   end:
   */
  override def visitSafeRefArray(node: SafeRefArray): Unit =
    val nullLabel = gen.newLabel()
    val endLabel = gen.newLabel()
    visitTerm(node.target)
    gen.dup()
    gen.visitJumpInsn(Opcodes.IFNULL, nullLabel)
    visitTerm(node.index)
    gen.arrayLoad(asmType(node.arrayType.base))
    node.arrayType.base match
      case bt: BasicType if bt != BasicType.VOID => gen.box(asmType(bt))
      case _ =>
    gen.goTo(endLabel)
    gen.visitLabel(nullLabel)
    gen.pop()
    gen.visitInsn(Opcodes.ACONST_NULL)
    gen.visitLabel(endLabel)
  
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
    val argTypes = node.method.arguments.map(asmType)
    emitArgumentsWithAdaptation(node.parameters, argTypes)
    val ownerType = AsmUtil.objectType(node.method.affiliation.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      argTypes*
    )
    val isInterface = node.method.affiliation.isInterface

    // Select correct invoke instruction based on method type
    if isInterface then
      gen.invokeInterface(ownerType, AsmMethod(node.method.name, methodDesc))
    else
      // Since JEP 181 (class file v55+) invokevirtual is legal for private
      // instance methods within a nest, which lets closures (nest members)
      // call the host's private methods
      gen.invokeVirtual(ownerType, AsmMethod(node.method.name, methodDesc))
  
  override def visitCallStatic(node: CallStatic): Unit =
    val argTypes = node.method.arguments.map(asmType)
    emitArgumentsWithAdaptation(node.parameters, argTypes)
    val ownerType = AsmUtil.objectType(node.target.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      argTypes*
    )
    if node.target.isInterface then
      // Static interface methods need an InterfaceMethodref constant (itf=true);
      // a plain Methodref makes the JVM throw IncompatibleClassChangeError.
      gen.visitMethodInsn(Opcodes.INVOKESTATIC, ownerType.getInternalName, node.method.name, methodDesc, true)
    else
      gen.invokeStatic(ownerType, AsmMethod(node.method.name, methodDesc))
  
  override def visitCallSuper(node: CallSuper): Unit =
    visitTerm(node.target)
    val argTypes = node.method.arguments.map(asmType)
    emitArgumentsWithAdaptation(node.params, argTypes)
    val ownerType = AsmUtil.objectType(node.method.affiliation.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      argTypes*
    )
    gen.visitMethodInsn(
      Opcodes.INVOKESPECIAL,
      ownerType.getInternalName,
      node.method.name,
      methodDesc,
      false
    )

  /**
   * Safe method call: target?.method(args)
   * Returns null if target is null, otherwise calls the method.
   *
   * Bytecode pattern:
   *   visitTerm(target)       // stack: [target]
   *   dup                     // stack: [target, target]
   *   ifnull nullLabel        // if null, jump to nullLabel
   *   // non-null path
   *   visitCall               // stack: [result]
   *   goto endLabel
   *   nullLabel:
   *   pop                     // pop the target
   *   aconst_null             // push null
   *   endLabel:
   */
  override def visitSafeCall(node: SafeCall): Unit =
    val nullLabel = gen.newLabel()
    val endLabel = gen.newLabel()

    // Evaluate target
    visitTerm(node.target)
    gen.dup()
    gen.visitJumpInsn(Opcodes.IFNULL, nullLabel)

    // Non-null path: call the method
    val argTypes = node.method.arguments.map(asmType)
    emitArgumentsWithAdaptation(node.parameters, argTypes)
    val ownerType = AsmUtil.objectType(node.method.affiliation.name)
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(node.method.returnType),
      argTypes*
    )
    val isInterface = node.method.affiliation.isInterface
    if isInterface then
      gen.invokeInterface(ownerType, AsmMethod(node.method.name, methodDesc))
    else
      gen.invokeVirtual(ownerType, AsmMethod(node.method.name, methodDesc))

    // Box primitive return type if needed (safe call always returns nullable)
    node.method.returnType match
      case bt: BasicType if bt != BasicType.VOID =>
        gen.box(asmType(bt))
      case _ => // Already an object type (or void: nothing on the stack)

    gen.goTo(endLabel)

    // Null path: pop target; a void call leaves nothing on the stack, so
    // only push null when the expression actually produces a value
    gen.visitLabel(nullLabel)
    gen.pop()
    if node.method.returnType != BasicType.VOID then
      gen.visitInsn(Opcodes.ACONST_NULL)

    gen.visitLabel(endLabel)

  /**
   * Safe field access: target?.field
   * Returns null if target is null, otherwise accesses the field.
   *
   * Bytecode pattern similar to SafeCall.
   */
  override def visitSafeFieldAccess(node: SafeFieldAccess): Unit =
    val nullLabel = gen.newLabel()
    val endLabel = gen.newLabel()

    // Evaluate target
    visitTerm(node.target)
    gen.dup()
    gen.visitJumpInsn(Opcodes.IFNULL, nullLabel)

    // Non-null path: access the field
    val ownerType = AsmUtil.objectType(node.field.affiliation.name)
    gen.getField(ownerType, node.field.name, asmType(node.field.`type`))

    // Box primitive field type if needed (safe access always returns nullable)
    node.field.`type` match
      case bt: BasicType if bt != BasicType.VOID =>
        gen.box(asmType(bt))
      case _ => // Already an object type

    gen.goTo(endLabel)

    // Null path: pop target and push null
    gen.visitLabel(nullLabel)
    gen.pop()
    gen.visitInsn(Opcodes.ACONST_NULL)

    gen.visitLabel(endLabel)

  override def visitAsInstanceOf(node: AsInstanceOf): Unit =
    visitTerm(node.target)
    (node.target.`type`, node.destination) match
      case (from: BasicType, to: BasicType) =>
        gen.cast(asmType(from), asmType(to))
      case (from: BasicType, _) =>
        gen.box(asmType(from))
        gen.checkCast(asmType(node.destination))
      case (_, to: BasicType) =>
        // Any reference-like source (ObjectType, TypeVariableType, wildcard...):
        // unbox emits the checkcast to the wrapper plus the xxxValue() call.
        // Falling through to a bare checkcast would emit 'checkcast I'.
        gen.unbox(asmType(to))
      case _ =>
        gen.checkCast(asmType(node.destination))
  
  override def visitInstanceOf(node: InstanceOf): Unit =
    visitTerm(node.target)
    gen.instanceOf(asmType(node.checked))
  
  override def visitListLiteral(node: ListLiteral): Unit =
    // Create ArrayList
    val listType = AsmUtil.objectType(AsmUtil.JavaUtilArrayList)
    val listCtor = AsmMethod.getMethod("void <init>(int)")
    val listAdd = AsmMethod.getMethod("boolean add(Object)")
    gen.newInstance(listType)
    gen.dup()
    gen.push(node.elements.length)
    gen.invokeConstructor(listType, listCtor)
    
    // Add elements
    for elem <- node.elements do
      gen.dup() // Duplicate list reference
      visitTerm(elem)
      // Box primitive if needed
      elem.`type` match
        case bt: BasicType => gen.box(asmType(bt))
        case _ => // Already an object
      gen.invokeVirtual(listType, listAdd)
      gen.pop() // Pop boolean result

  override def visitMapLiteral(node: MapLiteral): Unit =
    // Create LinkedHashMap (preserves literal entry order)
    val mapType = AsmUtil.objectType(AsmUtil.JavaUtilLinkedHashMap)
    val mapCtor = AsmMethod.getMethod("void <init>()")
    val mapPut = AsmMethod.getMethod("Object put(Object, Object)")
    gen.newInstance(mapType)
    gen.dup()
    gen.invokeConstructor(mapType, mapCtor)

    // Put entries
    for i <- node.keys.indices do
      gen.dup() // Duplicate map reference
      visitTerm(node.keys(i))
      node.keys(i).`type` match
        case bt: BasicType => gen.box(asmType(bt))
        case _ => // Already an object
      visitTerm(node.values(i))
      node.values(i).`type` match
        case bt: BasicType => gen.box(asmType(bt))
        case _ => // Already an object
      gen.invokeVirtual(mapType, mapPut)
      gen.pop() // Pop previous value
  
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
    emitArgumentsWithAdaptation(node.parameters, argTypes)
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

  override def visitNewArrayWithValues(node: NewArrayWithValues): Unit =
    val componentType = asmType(node.arrayType.component)
    gen.push(node.values.length)
    gen.newArray(componentType)
    for (i <- node.values.indices) {
      gen.dup()
      gen.push(i)
      visitTerm(node.values(i))
      gen.arrayStore(componentType)
    }

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
