package onion.compiler

import org.objectweb.asm.{ClassWriter, Label, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method => AsmMethod}
import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * ASM-based bytecode generator for the Onion language.
 * Generates JVM bytecode from the typed AST.
 */
class AsmCodeGeneration(config: CompilerConfig) extends BytecodeGenerator:
  import TypedAST._
  
  // Track local variable mappings from TypedAST indices to GeneratorAdapter's local indices
  private class LocalVarContext(gen: GeneratorAdapter):
    val indexMap = mutable.Map[Int, Int]()
    val parameterCount = mutable.Map[Int, Int]() // Track which indices are parameters
    
    def getOrAllocateSlot(typedIndex: Int, tp: AsmType): Int =
      indexMap.getOrElse(typedIndex, {
        val slot = gen.newLocal(tp)
        indexMap(typedIndex) = slot
        slot
      })
      
    def allocateSlot(typedIndex: Int, tp: AsmType): Int =
      val slot = gen.newLocal(tp)
      indexMap(typedIndex) = slot
      slot
      
    def isParameter(typedIndex: Int): Boolean =
      parameterCount.contains(typedIndex)
      
    def withParameters(isStatic: Boolean, argTypes: Array[TypedAST.Type]): LocalVarContext =
      // Parameters are already allocated by GeneratorAdapter
      // Slot 0 is 'this' for instance methods, first param for static
      var slot = if isStatic then 0 else 1
      for i <- argTypes.indices do
        val tp = asmType(argTypes(i))
        indexMap(i) = slot
        parameterCount(i) = i // Mark as parameter
        slot += tp.getSize()
      this
  
  // Special context for handling closures with captured variables
  private class ClosureLocalVarContext(
    gen: GeneratorAdapter,
    val closureClassName: String,
    val capturedVars: Seq[LocalBinding]
  ) extends LocalVarContext(gen):
    
    override def getOrAllocateSlot(typedIndex: Int, tp: AsmType): Int =
      // First check if this is a captured variable
      capturedVars.find(_.index == typedIndex) match
        case Some(_) =>
          // Return a special marker for captured variables
          // We'll handle loading them differently in emitExpression
          -typedIndex - 1000 // Use negative indices as markers
        case None =>
          // Regular local variable or parameter
          super.getOrAllocateSlot(typedIndex, tp)
          
    // Override to check for captured variables regardless of how they're accessed
    def isCapturedVariable(typedIndex: Int): Boolean =
      capturedVars.exists(_.index == typedIndex)
  
  private def toAsmModifier(mod: Int): Int =
    var access = 0
    if Modifier.isPublic(mod) then access |= Opcodes.ACC_PUBLIC
    if Modifier.isProtected(mod) then access |= Opcodes.ACC_PROTECTED
    if Modifier.isPrivate(mod) then access |= Opcodes.ACC_PRIVATE
    if Modifier.isStatic(mod) then access |= Opcodes.ACC_STATIC
    if Modifier.isFinal(mod) then access |= Opcodes.ACC_FINAL
    if Modifier.isAbstract(mod) then access |= Opcodes.ACC_ABSTRACT
    if Modifier.isSynchronized(mod) then access |= Opcodes.ACC_SYNCHRONIZED
    access

  private def asmType(tp: TypedAST.Type): AsmType = tp match
    case BasicType.VOID    => AsmType.VOID_TYPE
    case BasicType.BOOLEAN => AsmType.BOOLEAN_TYPE
    case BasicType.BYTE    => AsmType.BYTE_TYPE
    case BasicType.SHORT   => AsmType.SHORT_TYPE
    case BasicType.CHAR    => AsmType.CHAR_TYPE
    case BasicType.INT     => AsmType.INT_TYPE
    case BasicType.LONG    => AsmType.LONG_TYPE
    case BasicType.FLOAT   => AsmType.FLOAT_TYPE
    case BasicType.DOUBLE  => AsmType.DOUBLE_TYPE
    case ct: ClassType     => AsmType.getObjectType(ct.name.replace('.', '/'))
    case at: ArrayType     => AsmType.getType("[" + asmType(at.component).getDescriptor)
    case _: NullType       => AsmType.getObjectType("java/lang/Object")
    case _                 => throw new RuntimeException(s"Unsupported type: $tp")

  // Counter for generating unique closure class names
  private var closureCounter = 0
  
  // Collect generated closure classes
  private val generatedClosures = mutable.ArrayBuffer[CompiledClass]()
  
  override def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    generatedClosures.clear()
    val mainClasses = classes.map(generateClass)
    mainClasses ++ generatedClosures.toSeq

  private def generateClass(classDef: ClassDefinition): CompiledClass =
    val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES)
    
    // Generate class header
    // If class has no visibility modifiers, default to public
    val classModifier = if (classDef.modifier & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) == 0 
      then classDef.modifier | Modifier.PUBLIC 
      else classDef.modifier
    
    val access = if classDef.isInterface then
      toAsmModifier(classModifier) | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT
    else
      toAsmModifier(classModifier) | Opcodes.ACC_SUPER
      
    val name = classDef.name.replace('.', '/')
    val superName = if classDef.isInterface then
      "java/lang/Object"
    else if classDef.superClass != null then 
      classDef.superClass.name.replace('.', '/') 
    else 
      "java/lang/Object"
    val interfaces = classDef.interfaces.map(_.name.replace('.', '/')).toArray
    
    cw.visit(Opcodes.V17, access, name, null, superName, interfaces)
    
    // Generate fields
    for field <- classDef.fields do
      val fieldAccess = toAsmModifier(field.modifier)
      val fieldType = asmType(field.`type`)
      cw.visitField(fieldAccess, field.name, fieldType.getDescriptor, null, null)
    
    // Generate constructors (not for interfaces)
    if !classDef.isInterface then
      for ctorRef <- classDef.constructors do
        val ctor = ctorRef.asInstanceOf[ConstructorDefinition]
        codeConstructor(cw, ctor, name)
    
    // Generate methods
    for method <- classDef.methods do
      val methodDef = method.asInstanceOf[MethodDefinition]
      if classDef.isInterface then
        codeInterfaceMethod(cw, methodDef, name)
      else
        codeMethod(cw, methodDef, name)
    
    cw.visitEnd()
    
    // Return compiled class with output directory path from config
    val outputPath = config.outputDirectory
    CompiledClass(classDef.name, outputPath, cw.toByteArray)

  private def codeConstructor(cw: ClassWriter, ctor: ConstructorDefinition, className: String): Unit =
    val argTypes = ctor.arguments.map(asmType)
    val desc = AsmType.getMethodDescriptor(AsmType.VOID_TYPE, argTypes*)
    val mv = cw.visitMethod(toAsmModifier(ctor.modifier), "<init>", desc, null, null)
    // Use ASM's Method object to properly initialize GeneratorAdapter
    val asmMethod = AsmMethod("<init>", desc)
    val gen = new GeneratorAdapter(toAsmModifier(ctor.modifier), asmMethod, mv)
    
    gen.visitCode()
    
    // Set up local variable context
    val localVars = new LocalVarContext(gen).withParameters(false, ctor.arguments)
    
    // Handle super constructor call
    if ctor.superInitializer != null then
      gen.loadThis()
      for arg <- ctor.superInitializer.terms do
        emitExpressionWithContext(gen, arg, className, localVars)
      val superClass = if ctor.classType.superClass != null then 
        ctor.classType.superClass.name.replace('.', '/') 
      else "java/lang/Object"
      val superArgTypes = ctor.superInitializer.arguments.map(asmType)
      gen.invokeConstructor(
        AsmType.getObjectType(superClass),
        AsmMethod("<init>", AsmType.getMethodDescriptor(AsmType.VOID_TYPE, superArgTypes*))
      )
    else
      // Default super constructor
      gen.loadThis()
      gen.invokeConstructor(AsmType.getObjectType("java/lang/Object"), AsmMethod.getMethod("void <init>()"))
    
    // Process constructor body
    emitStatementsWithContext(gen, ctor.block.statements, className, localVars)
    
    gen.returnValue()
    gen.endMethod()
    
  private def codeInterfaceMethod(cw: ClassWriter, node: MethodDefinition, className: String): Unit =
    // Interface methods are abstract, so no method body
    val access = toAsmModifier(node.modifier) | Opcodes.ACC_ABSTRACT
    val argTypes = node.arguments.map(asmType)
    val returnType = asmType(node.returnType)
    val desc = AsmType.getMethodDescriptor(returnType, argTypes*)
    
    // Just visit the method without any code
    cw.visitMethod(access, node.name, desc, null, null)

  private def codeMethod(cw: ClassWriter, node: MethodDefinition, className: String): Unit =
    val access = toAsmModifier(node.modifier)
    val argTypes = node.arguments.map(asmType)
    val returnType = asmType(node.returnType)
    val desc = AsmType.getMethodDescriptor(returnType, argTypes*)
    val mv = cw.visitMethod(access, node.name, desc, null, null)
    // Use ASM's Method object to properly initialize GeneratorAdapter
    val asmMethod = AsmMethod(node.name, desc)
    val gen = new GeneratorAdapter(access, asmMethod, mv)
    
    gen.visitCode()
    
    // Set up local variable context
    val isStatic = (node.modifier & Modifier.STATIC) != 0
    val localVars = new LocalVarContext(gen).withParameters(isStatic, node.arguments)
    
    // Generate method body if present
    if node.block != null then
      emitStatementsWithContext(gen, node.block.statements, className, localVars)
    
    // Add default return if needed
    if node.block == null || !hasReturn(node.block.statements) then
      node.returnType match
        case BasicType.VOID => gen.returnValue()
        case BasicType.BOOLEAN => 
          gen.push(false)
          gen.returnValue()
        case BasicType.INT | BasicType.BYTE | BasicType.SHORT | BasicType.CHAR =>
          gen.push(0)
          gen.returnValue()
        case BasicType.LONG =>
          gen.push(0L)
          gen.returnValue()
        case BasicType.FLOAT =>
          gen.push(0.0f)
          gen.returnValue()
        case BasicType.DOUBLE =>
          gen.push(0.0d)
          gen.returnValue()
        case _ =>
          gen.visitInsn(Opcodes.ACONST_NULL)
          gen.returnValue()
    
    gen.endMethod()
    
  private def collectCapturedVariables(stmt: ActionStatement): Seq[LocalBinding] =
    val captured = mutable.LinkedHashMap[Int, LocalBinding]()
    
    def collectFromExpression(expr: Term): Unit = expr match
      case ref: RefLocal if ref.frame != 0 =>
        captured.getOrElseUpdate(ref.index, LocalBinding(ref.index, ref.`type`))
      case set: SetLocal if set.frame != 0 =>
        captured.getOrElseUpdate(set.index, LocalBinding(set.index, set.`type`))
        collectFromExpression(set.value)
      case bin: BinaryTerm =>
        collectFromExpression(bin.lhs)
        collectFromExpression(bin.rhs)
      case call: Call =>
        collectFromExpression(call.target)
        call.parameters.foreach(collectFromExpression)
      case _ => // Other expressions - add more cases as needed
        
    def collectFromStatement(s: ActionStatement): Unit = s match
      case expr: ExpressionActionStatement =>
        collectFromExpression(expr.term)
      case ret: Return if ret.term != null =>
        collectFromExpression(ret.term)
      case block: StatementBlock =>
        block.statements.foreach(collectFromStatement)
      case _ => // Other statements
        
    collectFromStatement(stmt)
    captured.values.toSeq
    
  private def hasReturn(stmts: Array[ActionStatement]): Boolean =
    stmts.exists {
      case _: Return => true
      case ifStmt: IfStatement => 
        ifStmt.elseStatement != null && 
        (ifStmt.thenStatement match {
          case block: StatementBlock => hasReturn(block.statements)
          case _ => false
        }) && 
        (ifStmt.elseStatement match {
          case block: StatementBlock => hasReturn(block.statements)
          case _ => false
        })
      case _ => false
    }

  private def emitStatements(gen: GeneratorAdapter, stmts: Array[ActionStatement], className: String): Unit =
    emitStatementsWithContext(gen, stmts, className, new LocalVarContext(gen))
    
  private def emitStatementsWithContext(gen: GeneratorAdapter, stmts: Array[ActionStatement], className: String, localVars: LocalVarContext): Unit =
    for stmt <- stmts do
      emitStatementWithContext(gen, stmt, className, localVars)

  private def emitStatement(gen: GeneratorAdapter, stmt: ActionStatement, className: String): Unit =
    emitStatementWithContext(gen, stmt, className, new LocalVarContext(gen))
    
  private def emitStatementWithContext(gen: GeneratorAdapter, stmt: ActionStatement, className: String, localVars: LocalVarContext): Unit = stmt match
    case expr: ExpressionActionStatement =>
      emitExpressionWithContext(gen, expr.term, className, localVars)
      // Pop the result if it's not void
      expr.term.`type` match
        case BasicType.VOID => // Nothing to pop
        case BasicType.LONG | BasicType.DOUBLE => gen.pop2()
        case _ => gen.pop()
    
    case ret: Return =>
      if ret.term != null then
        emitExpressionWithContext(gen, ret.term, className, localVars)
      gen.returnValue()
    
    case ifStmt: IfStatement =>
      val elseLabel = gen.newLabel()
      val endLabel = gen.newLabel()
      
      emitExpressionWithContext(gen, ifStmt.condition, className, localVars)
      gen.visitJumpInsn(Opcodes.IFEQ, elseLabel)
      
      emitStatementWithContext(gen, ifStmt.thenStatement, className, localVars)
      gen.visitJumpInsn(Opcodes.GOTO, endLabel)
      
      gen.visitLabel(elseLabel)
      if ifStmt.elseStatement != null then
        emitStatementWithContext(gen, ifStmt.elseStatement, className, localVars)
      
      gen.visitLabel(endLabel)
    
    case whileStmt: ConditionalLoop =>
      val startLabel = gen.newLabel()
      val endLabel = gen.newLabel()
      
      gen.visitLabel(startLabel)
      emitExpressionWithContext(gen, whileStmt.condition, className, localVars)
      gen.visitJumpInsn(Opcodes.IFEQ, endLabel)
      
      emitStatementWithContext(gen, whileStmt.stmt, className, localVars)
      gen.visitJumpInsn(Opcodes.GOTO, startLabel)
      
      gen.visitLabel(endLabel)
    
    case tryStmt: Try =>
      val tryStart = gen.mark()
      emitStatementWithContext(gen, tryStmt.tryStatement, className, localVars)
      val tryEnd = gen.mark()
      
      // Jump to end if no exception
      val endLabel = gen.newLabel()
      gen.goTo(endLabel)
      
      // Exception handlers
      for i <- tryStmt.catchTypes.indices do
        val catchType = tryStmt.catchTypes(i)
        val catchStmt = tryStmt.catchStatements(i)
        val handlerStart = gen.mark()
        val slot = localVars.getOrAllocateSlot(catchType.index, asmType(catchType.tp))
        gen.storeLocal(slot)
        emitStatementWithContext(gen, catchStmt, className, localVars)
        gen.catchException(tryStart, tryEnd, asmType(catchType.tp))
      
      gen.visitLabel(endLabel)
    
    case sync: Synchronized =>
      emitExpressionWithContext(gen, sync.term, className, localVars)
      gen.monitorEnter()
      
      val tryStart = gen.mark()
      emitStatementWithContext(gen, sync.statement, className, localVars)
      val tryEnd = gen.mark()
      
      emitExpressionWithContext(gen, sync.term, className, localVars)
      gen.monitorExit()
      val endLabel = gen.newLabel()
      gen.goTo(endLabel)
      
      // Exception handler to ensure monitor exit
      val handlerStart = gen.mark()
      emitExpressionWithContext(gen, sync.term, className, localVars)
      gen.monitorExit()
      gen.throwException()
      
      gen.catchException(tryStart, tryEnd, AsmType.getType(classOf[Throwable]))
      gen.visitLabel(endLabel)
    
    case throwStmt: Throw =>
      emitExpressionWithContext(gen, throwStmt.term, className, localVars)
      gen.throwException()
      
    case block: StatementBlock =>
      emitStatementsWithContext(gen, block.statements, className, localVars)
    
    case _: Break =>
      // TODO: Need to handle break statements with label tracking
      throw new UnsupportedOperationException("Break statements not yet implemented")
      
    case _: Continue =>
      // TODO: Need to handle continue statements with label tracking
      throw new UnsupportedOperationException("Continue statements not yet implemented")
    
    case _: NOP =>
      // No operation
      ()
    
    case _ =>
      throw new UnsupportedOperationException(s"Statement type not yet implemented: ${stmt.getClass.getName}")

  private def emitExpression(gen: GeneratorAdapter, expr: Term, className: String): Unit =
    emitExpressionWithContext(gen, expr, className, new LocalVarContext(gen))
    
  private def emitExpressionWithContext(gen: GeneratorAdapter, expr: Term, className: String, localVars: LocalVarContext): Unit = expr match
    // Literals
    case v: IntValue => gen.push(v.value)
    case v: LongValue => gen.push(v.value)
    case v: FloatValue => gen.push(v.value)
    case v: DoubleValue => gen.push(v.value)
    case v: BoolValue => gen.push(v.value)
    case v: StringValue => gen.push(v.value)
    case _: NullValue => gen.visitInsn(Opcodes.ACONST_NULL)
    
    // List literal
    case list: ListLiteral =>
      // Create ArrayList
      gen.newInstance(AsmType.getObjectType("java/util/ArrayList"))
      gen.dup()
      gen.push(list.elements.length)
      gen.invokeConstructor(
        AsmType.getObjectType("java/util/ArrayList"),
        AsmMethod.getMethod("void <init>(int)")
      )
      
      // Add elements
      for elem <- list.elements do
        gen.dup() // Duplicate list reference
        emitExpressionWithContext(gen, elem, className, localVars)
        // Box primitive if needed
        elem.`type` match
          case bt: BasicType => gen.box(asmType(bt))
          case _ => // Already an object
        gen.invokeVirtual(
          AsmType.getObjectType("java/util/ArrayList"),
          AsmMethod.getMethod("boolean add(Object)")
        )
        gen.pop() // Pop boolean result

    // Local variables
    case ref: RefLocal =>
      // Check if we're in a closure context and this might be a captured variable
      localVars match
        case closureCtx: ClosureLocalVarContext if closureCtx.isCapturedVariable(ref.index) =>
          // Load captured variable from field
          gen.loadThis()
          val capturedVar = closureCtx.capturedVars.find(_.index == ref.index).get
          gen.getField(
            AsmType.getObjectType(closureCtx.closureClassName),
            s"captured_${capturedVar.index}",
            asmType(capturedVar.tp)
          )
        case _ =>
          // Regular local variable
          if ref.frame == 0 then
            // Check if this is a parameter
            if localVars.isParameter(ref.index) then
              // For parameters, use loadArg which handles the internal tracking properly
              gen.loadArg(ref.index)
            else
              // For regular locals, use loadLocal
              val slot = localVars.getOrAllocateSlot(ref.index, asmType(ref.`type`))
              if slot < 0 then
                throw new RuntimeException(s"Invalid slot ${slot} for local variable index ${ref.index}")
              gen.loadLocal(slot)
          else
            // Frame != 0 means this variable will be captured by a closure
            // But in the outer method, we still treat it as a regular local
            val slot = localVars.getOrAllocateSlot(ref.index, asmType(ref.`type`))
            if slot < 0 then
              throw new RuntimeException(s"Invalid slot ${slot} for local variable index ${ref.index}")
            gen.loadLocal(slot)

    case set: SetLocal =>
      // Check if we're in a closure context and this might be a captured variable
      localVars match
        case closureCtx: ClosureLocalVarContext if closureCtx.isCapturedVariable(set.index) =>
          // Set captured variable field
          gen.loadThis()
          emitExpressionWithContext(gen, set.value, className, localVars)
          // Stack: [this, value]
          // We want: [value] after putfield, so duplicate value under this
          val valueType = asmType(set.`type`)
          if valueType.getSize() == 2 then
            // Long or double
            gen.dup2X1() // [value, this, value]
          else
            // Single slot value
            gen.dupX1()  // [value, this, value]
          val capturedVar = closureCtx.capturedVars.find(_.index == set.index).get
          gen.putField(
            AsmType.getObjectType(closureCtx.closureClassName),
            s"captured_${capturedVar.index}",
            asmType(capturedVar.tp)
          )
          // Stack: [value]
        case _ =>
          // Regular local variable
          if set.frame == 0 then
            emitExpressionWithContext(gen, set.value, className, localVars)
            gen.dup() // Duplicate for assignment result
            val slot = localVars.getOrAllocateSlot(set.index, asmType(set.`type`))
            gen.storeLocal(slot)
          else
            // Frame != 0 means this variable will be captured by a closure
            // But in the outer method, we still treat it as a regular local
            emitExpressionWithContext(gen, set.value, className, localVars)
            gen.dup() // Duplicate for assignment result
            val slot = localVars.getOrAllocateSlot(set.index, asmType(set.`type`))
            gen.storeLocal(slot)

    // Field access
    case ref: RefField =>
      emitExpressionWithContext(gen, ref.target, className, localVars)
      val ownerType = ref.target.`type` match
        case ct: ClassType => AsmType.getObjectType(ct.name.replace('.', '/'))
        case _ => throw new RuntimeException(s"Invalid field owner type: ${ref.target.`type`}")
      val fieldType = asmType(ref.field.`type`)
      gen.getField(ownerType, ref.field.name, fieldType)

    case set: SetField =>
      emitExpressionWithContext(gen, set.target, className, localVars)
      emitExpressionWithContext(gen, set.value, className, localVars)
      // Stack: target, value
      // We want to leave value on stack after putfield
      val valueType = asmType(set.value.`type`)
      if valueType.getSize() == 2 then
        // Long or double - duplicate value under target
        gen.dup2X1() // Stack: value, target, value
      else
        // Single slot value  
        gen.dupX1() // Stack: value, target, value
      val ownerType = set.target.`type` match
        case ct: ClassType => AsmType.getObjectType(ct.name.replace('.', '/'))
        case _ => throw new RuntimeException(s"Invalid field owner type: ${set.target.`type`}")
      val fieldType = asmType(set.field.`type`)
      gen.putField(ownerType, set.field.name, fieldType)
      // Stack: value (the result of the assignment)

    case ref: RefStaticField =>
      val ownerType = AsmType.getObjectType(ref.field.affiliation.name.replace('.', '/'))
      val fieldType = asmType(ref.field.`type`)
      gen.getStatic(ownerType, ref.field.name, fieldType)

    case set: SetStaticField =>
      emitExpressionWithContext(gen, set.value, className, localVars)
      gen.dup() // Duplicate value for assignment result
      val ownerType = AsmType.getObjectType(set.field.affiliation.name.replace('.', '/'))
      val fieldType = asmType(set.field.`type`)
      gen.putStatic(ownerType, set.field.name, fieldType)

    // Arrays
    case newArr: NewArray =>
      if newArr.parameters.length == 1 then
        // Simple array creation with size
        emitExpressionWithContext(gen, newArr.parameters(0), className, localVars)
        newArr.arrayType.component match
          case BasicType.BOOLEAN => gen.newArray(AsmType.BOOLEAN_TYPE)
          case BasicType.BYTE => gen.newArray(AsmType.BYTE_TYPE)
          case BasicType.SHORT => gen.newArray(AsmType.SHORT_TYPE)
          case BasicType.CHAR => gen.newArray(AsmType.CHAR_TYPE)
          case BasicType.INT => gen.newArray(AsmType.INT_TYPE)
          case BasicType.LONG => gen.newArray(AsmType.LONG_TYPE)
          case BasicType.FLOAT => gen.newArray(AsmType.FLOAT_TYPE)
          case BasicType.DOUBLE => gen.newArray(AsmType.DOUBLE_TYPE)
          case ct: ClassType => gen.newArray(AsmType.getObjectType(ct.name.replace('.', '/')))
          case at: ArrayType => gen.newArray(asmType(at.component))
          case _ => throw new RuntimeException(s"Cannot create array of type: ${newArr.arrayType.component}")
      else
        // Multi-dimensional array
        throw new UnsupportedOperationException("Multi-dimensional array creation not yet implemented")

    case len: ArrayLength =>
      emitExpressionWithContext(gen, len.target, className, localVars)
      gen.arrayLength()

    case ref: RefArray =>
      emitExpressionWithContext(gen, ref.target, className, localVars)
      emitExpressionWithContext(gen, ref.index, className, localVars)
      gen.arrayLoad(asmType(ref.`type`))

    case set: SetArray =>
      emitExpressionWithContext(gen, set.target, className, localVars)
      emitExpressionWithContext(gen, set.index, className, localVars)
      emitExpressionWithContext(gen, set.value, className, localVars)
      // Duplicate value for assignment result before storing
      val valueType = asmType(set.value.`type`)
      if valueType.getSize() == 2 then
        gen.dup2X2() // array, index, value -> value, array, index, value
        gen.pop2()
        gen.dup2X2()
        gen.pop2()
      else
        gen.dupX2() // array, index, value -> value, array, index, value
      gen.arrayStore(valueType)

    // Method calls - instance
    case call: Call =>
      // Push target
      emitExpressionWithContext(gen, call.target, className, localVars)
      
      // Push arguments
      for arg <- call.parameters do
        emitExpressionWithContext(gen, arg, className, localVars)
      
      // Get method info
      val ownerType = call.target.`type` match
        case ct: ClassType => AsmType.getObjectType(ct.name.replace('.', '/'))
        case _ => throw new RuntimeException(s"Invalid method owner type: ${call.target.`type`}")
      
      val methodDesc = AsmType.getMethodDescriptor(
        asmType(call.method.returnType),
        call.method.arguments.map(asmType)*
      )
      
      // Determine if it's an interface call
      val isInterface = call.target.`type` match
        case ct: ClassType => ct.isInterface
        case _ => false
      
      if isInterface then
        gen.invokeInterface(ownerType, AsmMethod(call.method.name, methodDesc))
      else
        gen.invokeVirtual(ownerType, AsmMethod(call.method.name, methodDesc))

    // Method calls - static
    case callStatic: CallStatic =>
      // Push arguments
      for arg <- callStatic.parameters do
        emitExpressionWithContext(gen, arg, className, localVars)
      
      val ownerType = AsmType.getObjectType(callStatic.method.affiliation.name.replace('.', '/'))
      val methodDesc = AsmType.getMethodDescriptor(
        asmType(callStatic.method.returnType),
        callStatic.method.arguments.map(asmType)*
      )
      
      gen.invokeStatic(ownerType, AsmMethod(callStatic.method.name, methodDesc))

    // Method calls - super
    case callSuper: CallSuper =>
      gen.loadThis()
      
      // Push arguments
      for arg <- callSuper.params do
        emitExpressionWithContext(gen, arg, className, localVars)
      
      // Get super class type
      val currentClass = callSuper.target.`type`.asInstanceOf[ClassType]
      val superType = if currentClass.superClass != null then
        AsmType.getObjectType(currentClass.superClass.name.replace('.', '/'))
      else
        AsmType.getObjectType("java/lang/Object")
      
      val methodDesc = AsmType.getMethodDescriptor(
        asmType(callSuper.method.returnType),
        callSuper.method.arguments.map(asmType)*
      )
      
      gen.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        superType.getInternalName,
        callSuper.method.name,
        methodDesc,
        false
      )

    // Object creation
    case newObj: NewObject =>
      val classType = AsmType.getObjectType(newObj.`type`.name.replace('.', '/'))
      gen.newInstance(classType)
      gen.dup()
      
      // Push constructor arguments
      for arg <- newObj.parameters do
        emitExpressionWithContext(gen, arg, className, localVars)
      
      val ctorDesc = AsmType.getMethodDescriptor(
        AsmType.VOID_TYPE,
        newObj.constructor.getArgs.map(asmType)*
      )
      
      gen.invokeConstructor(classType, AsmMethod("<init>", ctorDesc))

    // Type operations
    case instOf: InstanceOf =>
      emitExpressionWithContext(gen, instOf.target, className, localVars)
      val checkType = AsmType.getObjectType(instOf.checked.name.replace('.', '/'))
      gen.instanceOf(checkType)

    case cast: AsInstanceOf =>
      emitExpressionWithContext(gen, cast.target, className, localVars)
      cast.destination match
        case ct: ClassType =>
          gen.checkCast(AsmType.getObjectType(ct.name.replace('.', '/')))
        case at: ArrayType =>
          gen.checkCast(asmType(at))
        case bt: BasicType =>
          // Handle primitive casts
          cast.target.`type` match
            case BasicType.INT =>
              bt match
                case BasicType.BYTE => gen.visitInsn(Opcodes.I2B)
                case BasicType.SHORT => gen.visitInsn(Opcodes.I2S)
                case BasicType.CHAR => gen.visitInsn(Opcodes.I2C)
                case BasicType.LONG => gen.visitInsn(Opcodes.I2L)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.I2F)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.I2D)
                case _ => // No-op for same type
            case BasicType.LONG =>
              bt match
                case BasicType.INT => gen.visitInsn(Opcodes.L2I)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.L2F)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.L2D)
                case _ => // No-op
            case BasicType.FLOAT =>
              bt match
                case BasicType.INT => gen.visitInsn(Opcodes.F2I)
                case BasicType.LONG => gen.visitInsn(Opcodes.F2L)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.F2D)
                case _ => // No-op
            case BasicType.DOUBLE =>
              bt match
                case BasicType.INT => gen.visitInsn(Opcodes.D2I)
                case BasicType.LONG => gen.visitInsn(Opcodes.D2L)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.D2F)
                case _ => // No-op
            case _ => // Other types, no conversion needed
        case _ => // No-op for other types

    // Binary operations
    case bin: BinaryTerm =>
      val lhsType = bin.lhs.`type`
      
      // Handle short-circuit evaluation for logical operators
      bin.kind match
        case BinaryTerm.Constants.LOGICAL_AND =>
          val falseLabel = gen.newLabel()
          val endLabel = gen.newLabel()
          
          emitExpressionWithContext(gen, bin.lhs, className, localVars)
          gen.dup()
          gen.visitJumpInsn(Opcodes.IFEQ, falseLabel)
          gen.pop()
          emitExpressionWithContext(gen, bin.rhs, className, localVars)
          gen.visitJumpInsn(Opcodes.GOTO, endLabel)
          
          gen.visitLabel(falseLabel)
          // lhs is already false on stack
          
          gen.visitLabel(endLabel)
          
        case BinaryTerm.Constants.LOGICAL_OR =>
          val trueLabel = gen.newLabel()
          val endLabel = gen.newLabel()
          
          emitExpressionWithContext(gen, bin.lhs, className, localVars)
          gen.dup()
          gen.visitJumpInsn(Opcodes.IFNE, trueLabel)
          gen.pop()
          emitExpressionWithContext(gen, bin.rhs, className, localVars)
          gen.visitJumpInsn(Opcodes.GOTO, endLabel)
          
          gen.visitLabel(trueLabel)
          // lhs is already true on stack
          
          gen.visitLabel(endLabel)
          
        case _ =>
          // Regular binary operations - evaluate both operands
          emitExpressionWithContext(gen, bin.lhs, className, localVars)
          emitExpressionWithContext(gen, bin.rhs, className, localVars)
          
          bin.kind match
            // Arithmetic
            case BinaryTerm.Constants.ADD =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.IADD)
                case BasicType.LONG => gen.visitInsn(Opcodes.LADD)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.FADD)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.DADD)
                case _ => throw new RuntimeException(s"Invalid type for ADD: $lhsType")
            
            case BinaryTerm.Constants.SUBTRACT =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.ISUB)
                case BasicType.LONG => gen.visitInsn(Opcodes.LSUB)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.FSUB)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.DSUB)
                case _ => throw new RuntimeException(s"Invalid type for SUBTRACT: $lhsType")
            
            case BinaryTerm.Constants.MULTIPLY =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.IMUL)
                case BasicType.LONG => gen.visitInsn(Opcodes.LMUL)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.FMUL)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.DMUL)
                case _ => throw new RuntimeException(s"Invalid type for MULTIPLY: $lhsType")
            
            case BinaryTerm.Constants.DIVIDE =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.IDIV)
                case BasicType.LONG => gen.visitInsn(Opcodes.LDIV)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.FDIV)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.DDIV)
                case _ => throw new RuntimeException(s"Invalid type for DIVIDE: $lhsType")
            
            case BinaryTerm.Constants.MOD =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.IREM)
                case BasicType.LONG => gen.visitInsn(Opcodes.LREM)
                case BasicType.FLOAT => gen.visitInsn(Opcodes.FREM)
                case BasicType.DOUBLE => gen.visitInsn(Opcodes.DREM)
                case _ => throw new RuntimeException(s"Invalid type for MOD: $lhsType")
            
            // Bitwise
            case BinaryTerm.Constants.BIT_AND =>
              lhsType match
                case BasicType.INT | BasicType.BOOLEAN => gen.visitInsn(Opcodes.IAND)
                case BasicType.LONG => gen.visitInsn(Opcodes.LAND)
                case _ => throw new RuntimeException(s"Invalid type for BIT_AND: $lhsType")
            
            case BinaryTerm.Constants.BIT_OR =>
              lhsType match
                case BasicType.INT | BasicType.BOOLEAN => gen.visitInsn(Opcodes.IOR)
                case BasicType.LONG => gen.visitInsn(Opcodes.LOR)
                case _ => throw new RuntimeException(s"Invalid type for BIT_OR: $lhsType")
            
            case BinaryTerm.Constants.XOR =>
              lhsType match
                case BasicType.INT | BasicType.BOOLEAN => gen.visitInsn(Opcodes.IXOR)
                case BasicType.LONG => gen.visitInsn(Opcodes.LXOR)
                case _ => throw new RuntimeException(s"Invalid type for XOR: $lhsType")
            
            case BinaryTerm.Constants.BIT_SHIFT_L2 =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.ISHL)
                case BasicType.LONG => gen.visitInsn(Opcodes.LSHL)
                case _ => throw new RuntimeException(s"Invalid type for SHIFT_LEFT: $lhsType")
            
            case BinaryTerm.Constants.BIT_SHIFT_R2 =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.ISHR)
                case BasicType.LONG => gen.visitInsn(Opcodes.LSHR)
                case _ => throw new RuntimeException(s"Invalid type for SHIFT_RIGHT: $lhsType")
            
            case BinaryTerm.Constants.BIT_SHIFT_R3 =>
              lhsType match
                case BasicType.INT => gen.visitInsn(Opcodes.IUSHR)
                case BasicType.LONG => gen.visitInsn(Opcodes.LUSHR)
                case _ => throw new RuntimeException(s"Invalid type for UNSIGNED_SHIFT_RIGHT: $lhsType")
            
            // Comparison
            case op if op >= BinaryTerm.Constants.LESS_THAN && op <= BinaryTerm.Constants.NOT_EQUAL =>
              val jumpOpcode = op match
                case BinaryTerm.Constants.LESS_THAN => Opcodes.IF_ICMPLT
                case BinaryTerm.Constants.GREATER_THAN => Opcodes.IF_ICMPGT
                case BinaryTerm.Constants.LESS_OR_EQUAL => Opcodes.IF_ICMPLE
                case BinaryTerm.Constants.GREATER_OR_EQUAL => Opcodes.IF_ICMPGE
                case BinaryTerm.Constants.EQUAL => Opcodes.IF_ICMPEQ
                case BinaryTerm.Constants.NOT_EQUAL => Opcodes.IF_ICMPNE
              
              lhsType match
                case BasicType.INT | BasicType.BOOLEAN | BasicType.BYTE | BasicType.SHORT | BasicType.CHAR =>
                  val trueLabel = gen.newLabel()
                  val endLabel = gen.newLabel()
                  gen.visitJumpInsn(jumpOpcode, trueLabel)
                  gen.push(false)
                  gen.visitJumpInsn(Opcodes.GOTO, endLabel)
                  gen.visitLabel(trueLabel)
                  gen.push(true)
                  gen.visitLabel(endLabel)
                
                case BasicType.LONG =>
                  gen.visitInsn(Opcodes.LCMP)
                  val cmpOp = op match
                    case BinaryTerm.Constants.LESS_THAN => Opcodes.IFLT
                    case BinaryTerm.Constants.GREATER_THAN => Opcodes.IFGT
                    case BinaryTerm.Constants.LESS_OR_EQUAL => Opcodes.IFLE
                    case BinaryTerm.Constants.GREATER_OR_EQUAL => Opcodes.IFGE
                    case BinaryTerm.Constants.EQUAL => Opcodes.IFEQ
                    case BinaryTerm.Constants.NOT_EQUAL => Opcodes.IFNE
                  val trueLabel = gen.newLabel()
                  val endLabel = gen.newLabel()
                  gen.visitJumpInsn(cmpOp, trueLabel)
                  gen.push(false)
                  gen.visitJumpInsn(Opcodes.GOTO, endLabel)
                  gen.visitLabel(trueLabel)
                  gen.push(true)
                  gen.visitLabel(endLabel)
                
                case BasicType.FLOAT =>
                  gen.visitInsn(Opcodes.FCMPL)
                  val cmpOp = op match
                    case BinaryTerm.Constants.LESS_THAN => Opcodes.IFLT
                    case BinaryTerm.Constants.GREATER_THAN => Opcodes.IFGT
                    case BinaryTerm.Constants.LESS_OR_EQUAL => Opcodes.IFLE
                    case BinaryTerm.Constants.GREATER_OR_EQUAL => Opcodes.IFGE
                    case BinaryTerm.Constants.EQUAL => Opcodes.IFEQ
                    case BinaryTerm.Constants.NOT_EQUAL => Opcodes.IFNE
                  val trueLabel = gen.newLabel()
                  val endLabel = gen.newLabel()
                  gen.visitJumpInsn(cmpOp, trueLabel)
                  gen.push(false)
                  gen.visitJumpInsn(Opcodes.GOTO, endLabel)
                  gen.visitLabel(trueLabel)
                  gen.push(true)
                  gen.visitLabel(endLabel)
                
                case BasicType.DOUBLE =>
                  gen.visitInsn(Opcodes.DCMPL)
                  val cmpOp = op match
                    case BinaryTerm.Constants.LESS_THAN => Opcodes.IFLT
                    case BinaryTerm.Constants.GREATER_THAN => Opcodes.IFGT
                    case BinaryTerm.Constants.LESS_OR_EQUAL => Opcodes.IFLE
                    case BinaryTerm.Constants.GREATER_OR_EQUAL => Opcodes.IFGE
                    case BinaryTerm.Constants.EQUAL => Opcodes.IFEQ
                    case BinaryTerm.Constants.NOT_EQUAL => Opcodes.IFNE
                  val trueLabel = gen.newLabel()
                  val endLabel = gen.newLabel()
                  gen.visitJumpInsn(cmpOp, trueLabel)
                  gen.push(false)
                  gen.visitJumpInsn(Opcodes.GOTO, endLabel)
                  gen.visitLabel(trueLabel)
                  gen.push(true)
                  gen.visitLabel(endLabel)
                
                case _ => // Object comparison
                  if op == BinaryTerm.Constants.EQUAL then
                    val trueLabel = gen.newLabel()
                    val endLabel = gen.newLabel()
                    gen.visitJumpInsn(Opcodes.IF_ACMPEQ, trueLabel)
                    gen.push(false)
                    gen.visitJumpInsn(Opcodes.GOTO, endLabel)
                    gen.visitLabel(trueLabel)
                    gen.push(true)
                    gen.visitLabel(endLabel)
                  else if op == BinaryTerm.Constants.NOT_EQUAL then
                    val trueLabel = gen.newLabel()
                    val endLabel = gen.newLabel()
                    gen.visitJumpInsn(Opcodes.IF_ACMPNE, trueLabel)
                    gen.push(false)
                    gen.visitJumpInsn(Opcodes.GOTO, endLabel)
                    gen.visitLabel(trueLabel)
                    gen.push(true)
                    gen.visitLabel(endLabel)
                  else
                    throw new RuntimeException(s"Cannot compare objects with operator: $op")
            
            case BinaryTerm.Constants.ELVIS =>
              // Elvis operator: a ?: b returns a if a is not null, otherwise b
              // Stack already has: lhs, rhs
              if lhsType match { case BasicType.LONG | BasicType.DOUBLE => true; case _ => false } then gen.dup2X2() else gen.dupX1()
              gen.pop() // Remove duplicated rhs
              val nullLabel = gen.newLabel()
              val endLabel = gen.newLabel()
              gen.visitJumpInsn(Opcodes.IFNULL, nullLabel)
              // lhs is not null, pop rhs
              if lhsType match { case BasicType.LONG | BasicType.DOUBLE => true; case _ => false } then gen.pop2() else gen.pop()
              gen.visitJumpInsn(Opcodes.GOTO, endLabel)
              gen.visitLabel(nullLabel)
              // lhs is null, it's already popped by IFNULL, rhs is on stack
              gen.visitLabel(endLabel)
            
            case _ =>
              throw new RuntimeException(s"Unknown binary operation: ${bin.kind}")

    // Unary operations
    case unary: UnaryTerm =>
      emitExpressionWithContext(gen, unary.operand, className, localVars)
      
      val termType = unary.operand.`type`
      unary.kind match
        case UnaryTerm.Constants.NOT =>
          // Boolean NOT
          val trueLabel = gen.newLabel()
          val endLabel = gen.newLabel()
          gen.visitJumpInsn(Opcodes.IFEQ, trueLabel)
          gen.push(false)
          gen.visitJumpInsn(Opcodes.GOTO, endLabel)
          gen.visitLabel(trueLabel)
          gen.push(true)
          gen.visitLabel(endLabel)
        
        case UnaryTerm.Constants.MINUS =>
          termType match
            case BasicType.INT => gen.visitInsn(Opcodes.INEG)
            case BasicType.LONG => gen.visitInsn(Opcodes.LNEG)
            case BasicType.FLOAT => gen.visitInsn(Opcodes.FNEG)
            case BasicType.DOUBLE => gen.visitInsn(Opcodes.DNEG)
            case _ => throw new RuntimeException(s"Cannot negate type: $termType")
        
        case UnaryTerm.Constants.PLUS =>
          // Unary plus is a no-op
          ()
        
        case UnaryTerm.Constants.BIT_NOT =>
          termType match
            case BasicType.INT =>
              gen.push(-1)
              gen.visitInsn(Opcodes.IXOR)
            case BasicType.LONG =>
              gen.push(-1L)
              gen.visitInsn(Opcodes.LXOR)
            case _ => throw new RuntimeException(s"Cannot apply bitwise NOT to type: $termType")
        
        case _ =>
          throw new RuntimeException(s"Unknown unary operation: ${unary.kind}")

    // Control flow
    case begin: Begin =>
      if begin.terms.isEmpty then
        // Empty block - push void
        return
      
      // Emit all but last expression, popping their results
      for i <- 0 until begin.terms.length - 1 do
        emitExpressionWithContext(gen, begin.terms(i), className, localVars)
        begin.terms(i).`type` match
          case BasicType.VOID => // Nothing to pop
          case BasicType.LONG | BasicType.DOUBLE => gen.pop2()
          case _ => gen.pop()
      
      // Emit last expression without popping (it's the result)
      emitExpressionWithContext(gen, begin.terms.last, className, localVars)

    // Special references
    case _: This => gen.loadThis()
    
    case outer: OuterThis =>
      // Would need to access outer class instance
      throw new UnsupportedOperationException("Outer class access not yet implemented")
    
    // Closures
    case closure: NewClosure =>
      generateClosure(gen, closure, className, localVars)
    
    case _ =>
      throw new UnsupportedOperationException(s"Expression type not yet implemented: ${expr.getClass.getName}")

  private def generateClosure(gen: GeneratorAdapter, closure: NewClosure, currentClassName: String, localVars: LocalVarContext): Unit =
    closureCounter += 1
    val closureClassName = s"${currentClassName}$$Closure$closureCounter"
    val interfaceType = closure.`type`
    
    // Get captured variables from the closure frame
    val capturedVars = if closure.frame != null && closure.frame.entries.nonEmpty then 
      closure.frame.entries 
    else 
      // Fallback: collect variables with frame != 0 from the closure body
      collectCapturedVariables(closure.block)
    
    // Generate the closure class bytecode
    val closureBytes = generateClosureClass(closureClassName, interfaceType, closure.method, closure.block, capturedVars)
    
    // Store the generated closure class
    generatedClosures += CompiledClass(closureClassName.replace('/', '.'), config.outputDirectory, closureBytes)
    
    // Create instance of the closure class
    val closureType = AsmType.getObjectType(closureClassName)
    gen.newInstance(closureType)
    gen.dup()
    
    // Pass captured variables to constructor
    for capturedVar <- capturedVars do
      // In the outer method, these are regular locals
      // We need to load them to pass to the closure constructor
      // Check if it's a parameter first
      if localVars.isParameter(capturedVar.index) then
        gen.loadArg(capturedVar.index)
      else
        val slot = localVars.getOrAllocateSlot(capturedVar.index, asmType(capturedVar.tp))
        gen.loadLocal(slot)
    
    // Constructor descriptor
    val ctorDesc = AsmType.getMethodDescriptor(
      AsmType.VOID_TYPE,
      capturedVars.map(v => asmType(v.tp)).toArray*
    )
    
    gen.visitMethodInsn(
      Opcodes.INVOKESPECIAL,
      closureClassName,
      "<init>",
      ctorDesc,
      false
    )

  private def generateClosureClass(
    className: String,
    interfaceType: ClassType, 
    method: TypedAST.Method,
    block: ActionStatement,
    capturedVars: Seq[LocalBinding]
  ): Array[Byte] =
    val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
    
    // Generate class header - implement the interface
    cw.visit(
      Opcodes.V17,
      Opcodes.ACC_PUBLIC,
      className,
      null,
      "java/lang/Object",
      Array(interfaceType.name.replace('.', '/'))
    )
    
    // Generate fields for captured variables
    for capturedVar <- capturedVars do
      val fieldType = asmType(capturedVar.tp)
      cw.visitField(
        Opcodes.ACC_PRIVATE,
        s"captured_${capturedVar.index}",
        fieldType.getDescriptor,
        null,
        null
      )
    
    // Generate constructor
    generateClosureConstructor(cw, className, capturedVars)
    
    // Generate the interface method implementation
    generateClosureMethod(cw, className, method, block, capturedVars)
    
    cw.visitEnd()
    cw.toByteArray

  private def generateClosureConstructor(cw: ClassWriter, className: String, capturedVars: Seq[LocalBinding]): Unit =
    val ctorDesc = AsmType.getMethodDescriptor(
      AsmType.VOID_TYPE,
      capturedVars.map(v => asmType(v.tp)).toArray*
    )
    
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc, null, null)
    val gen = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, "<init>", ctorDesc)
    
    gen.visitCode()
    
    // Call super constructor
    gen.loadThis()
    gen.invokeConstructor(AsmType.getObjectType("java/lang/Object"), AsmMethod.getMethod("void <init>()"))
    
    // Initialize captured variable fields
    for (capturedVar, paramIndex) <- capturedVars.zipWithIndex do
      gen.loadThis()
      gen.loadArg(paramIndex)
      gen.putField(
        AsmType.getObjectType(className),
        s"captured_${capturedVar.index}",
        asmType(capturedVar.tp)
      )
    
    gen.returnValue()
    gen.endMethod()

  private def generateClosureMethod(
    cw: ClassWriter,
    className: String,
    method: TypedAST.Method,
    block: ActionStatement,
    capturedVars: Seq[LocalBinding]
  ): Unit =
    val methodDesc = AsmType.getMethodDescriptor(
      asmType(method.returnType),
      method.arguments.map(asmType)*
    )
    
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.name, methodDesc, null, null)
    val gen = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, method.name, methodDesc)
    
    gen.visitCode()
    
    // Create a special local var context for closures
    val closureLocalVars = new ClosureLocalVarContext(gen, className, capturedVars)
      .withParameters(false, method.arguments)
    
    
    // Generate method body
    emitStatementWithContext(gen, block, className, closureLocalVars)
    
    // Add default return if needed
    method.returnType match
      case BasicType.VOID => gen.returnValue()
      case _ => 
        if !hasReturn(Array(block)) then
          // Method should return a value but doesn't
          method.returnType match
            case BasicType.BOOLEAN => 
              gen.push(false)
              gen.returnValue()
            case BasicType.INT | BasicType.BYTE | BasicType.SHORT | BasicType.CHAR =>
              gen.push(0)
              gen.returnValue()
            case BasicType.LONG =>
              gen.push(0L)
              gen.returnValue()
            case BasicType.FLOAT =>
              gen.push(0.0f)
              gen.returnValue()
            case BasicType.DOUBLE =>
              gen.push(0.0d)
              gen.returnValue()
            case _ =>
              gen.visitInsn(Opcodes.ACONST_NULL)
              gen.returnValue()
    
    gen.endMethod()