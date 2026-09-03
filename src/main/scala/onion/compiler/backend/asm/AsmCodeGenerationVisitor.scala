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

  // A call site's argument types, descriptor and owner depend only on the Method, and a
  // program calls the same few methods over and over; building the descriptor string and
  // the owner Type per call site was a visible share of code generation.
  private final class CallShape(val argTypes: Array[AsmType], val descriptor: String, val owner: AsmType)
  private val callShapes = new java.util.IdentityHashMap[TypedAST.Method, CallShape]()
  private def callShape(method: TypedAST.Method, ownerName: String): CallShape = {
    val cached = callShapes.get(method)
    if (cached != null && cached.owner.getInternalName == AsmUtil.internalName(ownerName)) cached
    else {
      val argTypes = method.arguments.map(asmType)(using AsmUtil.asmTypeTag)
      val shape = new CallShape(argTypes, AsmType.getMethodDescriptor(asmType(method.returnType), argTypes*), AsmUtil.objectType(ownerName))
      callShapes.put(method, shape)
      shape
    }
  }

  // Emit line number if location exists and line changed
  private def emitLineNumber(location: Location): Unit =
    location match
      case null => // No location info
      case loc if loc.line != lastEmittedLine =>
        val label = gen.mark()
        gen.visitLineNumber(loc.line, label)
        lastEmittedLine = loc.line
      case _ => // Same line, skip

  // Emit method arguments with boxing/unboxing adaptation. `pendingTypes` lists
  // the types of values already sitting on the operand stack below these
  // arguments (e.g. a call receiver), bottom to top.
  //
  // A `try`/`catch` used as a non-first argument (or nested inside one) is
  // dangerous: the JVM clears the operand stack when it dispatches to the
  // catch handler, silently discarding any earlier arguments already pushed
  // underneath (issue #669). So whenever an argument may run its own `try`,
  // everything currently on the stack is spilled into fresh locals first and
  // reloaded afterward — this preserves left-to-right evaluation order while
  // guaranteeing nothing sits on the stack across the try's protected region.
  private def emitArgumentsWithAdaptation(
    params: Array[Term],
    expectedTypes: Array[AsmType],
    pendingTypes: Array[AsmType] = AsmUtil.noAsmTypes
  ): Unit =
    if TermContainsTry.currentMethodHasNoTry then
      // Nothing to spill around: emit the arguments in order.
      var i = 0
      while i < params.length do
        visitTerm(params(i))
        asmCodeGen.adaptValueOnStack(gen, params(i).`type`, expectedTypes(i))
        i += 1
      return
    val pending = scala.collection.mutable.ArrayBuffer[AsmType](pendingTypes*)
    var i = 0
    while i < params.length do
      val param = params(i)
      val expectedType = expectedTypes(i)
      if pending.nonEmpty && TermContainsTry.contains(param) then
        val slots = new Array[Int](pending.length)
        for j <- (pending.length - 1) to 0 by -1 do
          val slot = gen.newLocal(pending(j))
          gen.storeLocal(slot)
          slots(j) = slot
        visitTerm(param)
        asmCodeGen.adaptValueOnStack(gen, param.`type`, expectedType)
        val ownSlot = gen.newLocal(expectedType)
        gen.storeLocal(ownSlot)
        for j <- slots.indices do
          gen.loadLocal(slots(j))
        gen.loadLocal(ownSlot)
      else
        visitTerm(param)
        asmCodeGen.adaptValueOnStack(gen, param.`type`, expectedType)
      pending += expectedType
      i += 1

  // Expression visitors
  override def visitArrayLength(node: ArrayLength): Unit =
    visitTerm(node.target)
    gen.arrayLength()
  
  override def visitRefArray(node: RefArray): Unit =
    if TermContainsTry.contains(node.index) then
      // See visitSetArray: the target can't stay live on the operand stack
      // across an index expression that runs its own try/catch, since the
      // JVM clears the stack when dispatching to an exception handler.
      visitTerm(node.target)
      val targetSlot = gen.newLocal(asmType(node.target.`type`))
      gen.storeLocal(targetSlot)
      visitTerm(node.index)
      val indexSlot = gen.newLocal(AsmType.INT_TYPE)
      gen.storeLocal(indexSlot)
      gen.loadLocal(targetSlot)
      gen.loadLocal(indexSlot)
      gen.arrayLoad(asmType(node.`type`))
    else
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
    // A nullable primitive (e.g. Int?) is a boxed value at runtime but its
    // non-null result type is the primitive, so unbox to match the static type.
    if node.`type`.isBasicType then gen.unbox(asmType(node.`type`))

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
    if TermContainsTry.contains(node.index) then
      // See visitRefArray: the target can't stay live on the operand stack
      // across an index expression that runs its own try/catch, since the
      // JVM clears the stack when dispatching to an exception handler.
      val targetSlot = gen.newLocal(asmType(node.target.`type`))
      gen.storeLocal(targetSlot)
      visitTerm(node.index)
      val indexSlot = gen.newLocal(AsmType.INT_TYPE)
      gen.storeLocal(indexSlot)
      gen.loadLocal(targetSlot)
      gen.loadLocal(indexSlot)
      gen.arrayLoad(asmType(node.arrayType.base))
    else
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
    val valueType = asmType(node.`type`)
    if TermContainsTry.contains(node.index) || TermContainsTry.contains(node.value) then
      // See visitSetField: the target/index can't stay live on the operand
      // stack across an index or value that runs its own try/catch, since
      // the JVM clears the stack when dispatching to an exception handler.
      // (The read-side sibling of this gap -- visitRefArray checking only
      // its index -- was fixed already; this write side was missing the
      // index check and only guarded against the value.)
      visitTerm(node.target)
      val targetSlot = gen.newLocal(asmType(node.target.`type`))
      gen.storeLocal(targetSlot)
      visitTerm(node.index)
      val indexSlot = gen.newLocal(AsmType.INT_TYPE)
      gen.storeLocal(indexSlot)
      visitTerm(node.value)
      val valueSlot = gen.newLocal(valueType)
      gen.storeLocal(valueSlot)
      gen.loadLocal(targetSlot)
      gen.loadLocal(indexSlot)
      gen.loadLocal(valueSlot)
      gen.arrayStore(valueType)
      // Assignment is itself an expression; leave the assigned value as its result.
      gen.loadLocal(valueSlot)
    else
      visitTerm(node.target)
      visitTerm(node.index)
      visitTerm(node.value)
      // Duplicate value for return
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
    val shape = callShape(node.method, node.method.affiliation.name)
    val argTypes = shape.argTypes
    emitArgumentsWithAdaptation(node.parameters, argTypes, Array(asmType(node.target.`type`)))
    val ownerType = shape.owner
    val methodDesc = shape.descriptor
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
    val shape = callShape(node.method, node.target.name)
    val argTypes = shape.argTypes
    emitArgumentsWithAdaptation(node.parameters, argTypes)
    val ownerType = shape.owner
    val methodDesc = shape.descriptor
    if node.target.isInterface then
      // Static interface methods need an InterfaceMethodref constant (itf=true);
      // a plain Methodref makes the JVM throw IncompatibleClassChangeError.
      gen.visitMethodInsn(Opcodes.INVOKESTATIC, ownerType.getInternalName, node.method.name, methodDesc, true)
    else
      gen.invokeStatic(ownerType, AsmMethod(node.method.name, methodDesc))
  
  override def visitCallSuper(node: CallSuper): Unit =
    visitTerm(node.target)
    val argTypes = node.method.arguments.map(asmType)
    emitArgumentsWithAdaptation(node.params, argTypes, Array(asmType(node.target.`type`)))
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

    // Non-null path: call the method. The target survives the null check on
    // the stack (the dup'd copy IFNULL consumed was the other one), so it is
    // a pending operand across the arguments just like visitCall's receiver.
    val argTypes = node.method.arguments.map(asmType)
    emitArgumentsWithAdaptation(node.parameters, argTypes, Array(asmType(node.target.`type`)))
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

    // Non-null path: access the field. The array `length` pseudo-field has no
    // affiliation class and must be read with ARRAYLENGTH, not GETFIELD — a bare
    // getField here dereferenced affiliation.name and crashed the compiler (I0000)
    // on `arr?.length`.
    if (node.field.affiliation == null)
      gen.arrayLength()
    else
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

    if node.elements.exists(TermContainsTry.contains) then
      // See visitNewArrayWithValues: the list reference can't stay on the
      // operand stack across an element that may run its own try/catch, since
      // the JVM clears the stack when dispatching to an exception handler.
      // Spill it into a local instead and reload it for each add().
      val objectType = AsmUtil.objectType(AsmUtil.JavaLangObject)
      val listSlot = gen.newLocal(listType)
      gen.storeLocal(listSlot)
      for elem <- node.elements do
        visitTerm(elem)
        elem.`type` match
          case bt: BasicType => gen.box(asmType(bt))
          case _ => // Already an object
        val elemSlot = gen.newLocal(objectType)
        gen.storeLocal(elemSlot)
        gen.loadLocal(listSlot)
        gen.loadLocal(elemSlot)
        gen.invokeVirtual(listType, listAdd)
        gen.pop() // Pop boolean result
      gen.loadLocal(listSlot)
    else
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

    if (node.keys.iterator ++ node.values.iterator).exists(TermContainsTry.contains) then
      // See visitListLiteral/visitNewArrayWithValues: the map reference can't
      // stay on the operand stack across a key/value that may run its own
      // try/catch. Spill it into a local instead and reload it for each put().
      val objectType = AsmUtil.objectType(AsmUtil.JavaLangObject)
      val mapSlot = gen.newLocal(mapType)
      gen.storeLocal(mapSlot)
      for i <- node.keys.indices do
        visitTerm(node.keys(i))
        node.keys(i).`type` match
          case bt: BasicType => gen.box(asmType(bt))
          case _ => // Already an object
        val keySlot = gen.newLocal(objectType)
        gen.storeLocal(keySlot)
        visitTerm(node.values(i))
        node.values(i).`type` match
          case bt: BasicType => gen.box(asmType(bt))
          case _ => // Already an object
        val valueSlot = gen.newLocal(objectType)
        gen.storeLocal(valueSlot)
        gen.loadLocal(mapSlot)
        gen.loadLocal(keySlot)
        gen.loadLocal(valueSlot)
        gen.invokeVirtual(mapType, mapPut)
        gen.pop() // Pop previous value
      gen.loadLocal(mapSlot)
    else
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
    // Emit the line number so a runtime exception thrown while evaluating a
    // local's initializer (`val bad: String = n!!`) maps to the declaration's
    // line rather than the previous statement's. Sibling statement visitors do
    // this; SetLocal (which backs a local var/val declaration) was missing it.
    emitLineNumber(node.location)
    asmCodeGen.emitSetLocal(gen, node, className, localVars)
  
  override def visitNewClosure(node: NewClosure): Unit =
    asmCodeGen.emitNewClosure(gen, node, className, localVars)
  
  override def visitRefField(node: RefField): Unit =
    visitTerm(node.target)
    val ownerType = AsmUtil.objectType(node.field.affiliation.name)
    gen.getField(ownerType, node.field.name, asmType(node.field.`type`))
  
  override def visitSetField(node: SetField): Unit =
    val valueType = asmType(node.`type`)
    val ownerType = AsmUtil.objectType(node.field.affiliation.name)
    if TermContainsTry.contains(node.value) then
      // The target must survive node.value's evaluation, but it can't stay on
      // the operand stack if node.value runs its own try/catch: the JVM
      // clears the stack when dispatching to an exception handler, so a bare
      // target pushed before the try region would vanish on the exceptional
      // path, desyncing the merged stack shape from the normal path (issue
      // #745, the field-assignment sibling of issue #669's call-argument fix).
      // Stash it in a local instead and reload it once the value is settled.
      visitTerm(node.target)
      val targetSlot = gen.newLocal(asmType(node.target.`type`))
      gen.storeLocal(targetSlot)
      visitTerm(node.value)
      asmCodeGen.adaptValueOnStack(gen, node.value.`type`, valueType)
      val valueSlot = gen.newLocal(valueType)
      gen.storeLocal(valueSlot)
      gen.loadLocal(targetSlot)
      gen.loadLocal(valueSlot)
      gen.putField(ownerType, node.field.name, valueType)
      // Assignment is itself an expression; leave the assigned value as its result.
      gen.loadLocal(valueSlot)
    else
      visitTerm(node.target)
      visitTerm(node.value)
      // Duplicate value for return
      asmCodeGen.adaptValueOnStack(gen, node.value.`type`, valueType)
      if valueType.getSize() == 2 then
        gen.dup2X1()
      else
        gen.dupX1()
      gen.putField(ownerType, node.field.name, valueType)
  
  override def visitNewObject(node: NewObject): Unit =
    val classType = AsmUtil.objectType(node.constructor.affiliation.name)
    val argTypes = node.constructor.getArgs.map(asmType)
    if node.parameters.exists(TermContainsTry.contains) then
      // Unlike an ordinary receiver (visitCall), the value `new` pushes is not
      // yet a real object -- the verifier tracks it as "uninitialized(new-site)"
      // until the matching invokespecial runs, and that special type cannot
      // safely cross a try/catch merge the way a spilled, already-initialized
      // reference can (spilling it into a local and reloading it, as
      // emitArgumentsWithAdaptation does for visitCall/visitSafeCall, produced
      // bytecode the JVM verifier rejected: "Inconsistent stackmap frames").
      // So no `new` may be in flight while an argument's own try runs: every
      // argument is evaluated into a plain local first, and only then does
      // `new`/`dup`/reload-args/<init> run as one straight, branch-free run.
      val argSlots = new Array[Int](node.parameters.length)
      for i <- node.parameters.indices do
        visitTerm(node.parameters(i))
        asmCodeGen.adaptValueOnStack(gen, node.parameters(i).`type`, argTypes(i))
        val slot = gen.newLocal(argTypes(i))
        gen.storeLocal(slot)
        argSlots(i) = slot
      gen.newInstance(classType)
      gen.dup()
      for slot <- argSlots do gen.loadLocal(slot)
      gen.invokeConstructor(classType, AsmMethod("<init>", AsmType.getMethodDescriptor(AsmType.VOID_TYPE, argTypes*)))
    else
      gen.newInstance(classType)
      gen.dup()
      emitArgumentsWithAdaptation(node.parameters, argTypes)
      gen.invokeConstructor(classType, AsmMethod("<init>", AsmType.getMethodDescriptor(AsmType.VOID_TYPE, argTypes*)))
  
  override def visitNewArray(node: NewArray): Unit =
    val componentType = asmType(node.arrayType.component)
    if node.parameters.length == 1 then
      visitTerm(node.parameters(0))
      gen.newArray(componentType)
    else if node.parameters.exists(TermContainsTry.contains) then
      // See visitNewObject: a later dimension's own try/catch would otherwise
      // discard earlier dimensions already sitting on the operand stack,
      // since the JVM clears the stack when dispatching to an exception
      // handler. So every dimension is evaluated into a plain local first,
      // and only then are they reloaded for multianewarray.
      val slots = new Array[Int](node.parameters.length)
      for i <- node.parameters.indices do
        visitTerm(node.parameters(i))
        val slot = gen.newLocal(AsmType.INT_TYPE)
        gen.storeLocal(slot)
        slots(i) = slot
      for slot <- slots do gen.loadLocal(slot)
      gen.visitMultiANewArrayInsn(asmType(node.arrayType).getDescriptor, node.parameters.length)
    else
      for param <- node.parameters do
        visitTerm(param)
      gen.visitMultiANewArrayInsn(asmType(node.arrayType).getDescriptor, node.parameters.length)

  override def visitNewArrayWithValues(node: NewArrayWithValues): Unit =
    val componentType = asmType(node.arrayType.component)
    gen.push(node.values.length)
    gen.newArray(componentType)
    if node.values.exists(TermContainsTry.contains) then
      // See visitSetField/visitSetArray: nothing may be left on the operand
      // stack (array ref, index, or otherwise) while an element's own
      // evaluation runs a try/catch, since the JVM clears the stack when
      // dispatching to an exception handler — it must be evaluated with an
      // empty stack, then the array ref/index/value reloaded from locals
      // for the store.
      val arraySlot = gen.newLocal(asmType(node.arrayType))
      gen.storeLocal(arraySlot)
      for (i <- node.values.indices) {
        visitTerm(node.values(i))
        val valueSlot = gen.newLocal(componentType)
        gen.storeLocal(valueSlot)
        gen.loadLocal(arraySlot)
        gen.push(i)
        gen.loadLocal(valueSlot)
        gen.arrayStore(componentType)
      }
      gen.loadLocal(arraySlot)
    else
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
