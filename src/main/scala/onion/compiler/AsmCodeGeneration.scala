package onion.compiler

import org.objectweb.asm.{ClassWriter, Label, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method => AsmMethod}
import onion.compiler.bytecode.{AsmUtil, BridgeMethodEmitter, ClosureCodegen, LocalVarContext, ClosureLocalVarContext, MethodEmitter}
import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * ASM-based bytecode generator for the Onion language.
 * Generates JVM bytecode from the typed AST.
 */
class AsmCodeGeneration(config: CompilerConfig) extends BytecodeGenerator:
  import TypedAST._
  import AsmCodeGeneration._


  private[compiler] def toAsmModifier(mod: Int): Int =
    var access = 0
    if Modifier.isPublic(mod) then access |= Opcodes.ACC_PUBLIC
    if Modifier.isProtected(mod) then access |= Opcodes.ACC_PROTECTED
    if Modifier.isPrivate(mod) then access |= Opcodes.ACC_PRIVATE
    if Modifier.isStatic(mod) then access |= Opcodes.ACC_STATIC
    if Modifier.isFinal(mod) then access |= Opcodes.ACC_FINAL
    if Modifier.isAbstract(mod) then access |= Opcodes.ACC_ABSTRACT
    if Modifier.isSynchronized(mod) then access |= Opcodes.ACC_SYNCHRONIZED
    access

  // Delegate to companion object
  def asmType(tp: TypedAST.Type): AsmType = AsmCodeGeneration.asmType(tp)
  def boxClassName(tp: TypedAST.Type): String = AsmCodeGeneration.boxClassName(tp)
  def boxAsmType(tp: TypedAST.Type): AsmType = AsmCodeGeneration.boxAsmType(tp)

  private def boxedValueType(tp: TypedAST.Type): AsmType = tp match
    case BasicType.INT     => AsmType.INT_TYPE
    case BasicType.LONG    => AsmType.LONG_TYPE
    case BasicType.DOUBLE  => AsmType.DOUBLE_TYPE
    case BasicType.FLOAT   => AsmType.FLOAT_TYPE
    case _                 => AsmUtil.objectType(AsmUtil.JavaLangObject)

  private[compiler] def isReferenceAsmType(tp: AsmType): Boolean =
    tp.getSort == AsmType.OBJECT || tp.getSort == AsmType.ARRAY

  private[compiler] def adaptValueOnStack(gen: GeneratorAdapter, actual: TypedAST.Type, expected: AsmType): Unit =
    actual match
      case bt: BasicType if isReferenceAsmType(expected) =>
        val primitiveType = asmType(bt)
        // long/double are 2-slot types - ASM's valueOf() doesn't handle them correctly
        // Call the static valueOf methods directly instead
        primitiveType.getSort match
          case AsmType.LONG =>
            gen.invokeStatic(
              AsmCodeGeneration.LongBoxedType,
              AsmCodeGeneration.LongValueOfMethod
            )
          case AsmType.DOUBLE =>
            gen.invokeStatic(
              AsmCodeGeneration.DoubleBoxedType,
              AsmCodeGeneration.DoubleValueOfMethod
            )
          case _ =>
            gen.valueOf(primitiveType)
      case _ if !actual.isBasicType && !isReferenceAsmType(expected) =>
        gen.unbox(expected)
      case _ =>
        ()

  // Counter for generating unique closure class names
  private var closureCounter = 0
  
  // Collect generated closure classes
  private val generatedClosures = mutable.ArrayBuffer[CompiledClass]()

  private def nextClosureId(): Int = {
    closureCounter += 1
    closureCounter
  }

  private val closureCodegen =
    new ClosureCodegen(this, config.outputDirectory, () => nextClosureId(), compiled => generatedClosures += compiled)
  private val bridgeMethodEmitter = new BridgeMethodEmitter(this)
  
  override def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    generatedClosures.clear()
    val mainClasses = classes.map(generateClass)
    mainClasses ++ generatedClosures.toSeq

  private def sourceFileName(classDef: ClassDefinition): String =
    val simpleName = classDef.name.split('.').last
    s"$simpleName.on"

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
      
    val name = AsmUtil.internalName(classDef.name)
    val superName = if classDef.isInterface then
      "java/lang/Object"
    else if classDef.superClass != null then 
      AsmUtil.internalName(classDef.superClass.name) 
    else 
      "java/lang/Object"
    val interfaces: Array[String] = classDef.interfaces.map(i => AsmUtil.internalName(i.name)).toArray

    cw.visit(Opcodes.V17, access, name, null, superName, interfaces)
    cw.visitSource(sourceFileName(classDef), null)

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

    val staticInitializers = classDef.staticInitializers
    // Generate static initializer for enums
    if Modifier.isEnum(classDef.modifier) then
      codeEnumClinit(cw, classDef, name, staticInitializers)
    else if staticInitializers.nonEmpty then
      codeStaticInitializer(cw, name, staticInitializers)

    // Generate methods
    for method <- classDef.methods do
      val methodDef = method.asInstanceOf[MethodDefinition]
      if classDef.isInterface || Modifier.isAbstract(methodDef.modifier) then
        codeInterfaceMethod(cw, methodDef, name)
      else
        codeMethod(cw, methodDef, name)

    if !classDef.isInterface then
      bridgeMethodEmitter.emitBridges(cw, classDef)
    
    cw.visitEnd()
    
    // Return compiled class with output directory path from config
    val outputPath = config.outputDirectory
    CompiledClass(classDef.name, outputPath, cw.toByteArray)

  private def codeConstructor(cw: ClassWriter, ctor: ConstructorDefinition, className: String): Unit =
    val argTypes = ctor.arguments.map(asmType)
    val gen = MethodEmitter.newGenerator(cw, toAsmModifier(ctor.modifier), "<init>", AsmType.VOID_TYPE, argTypes)

    // Emit line number for constructor declaration
    if ctor.location != null then
      val label = gen.mark()
      gen.visitLineNumber(ctor.location.line, label)

    val localVars = new LocalVarContext(gen).withParameters(false, argTypes)

    if ctor.superInitializer != null then
      gen.loadThis()
      var i = 0
      while i < ctor.superInitializer.terms.length do
        val arg = ctor.superInitializer.terms(i)
        emitExpressionWithContext(gen, arg, className, localVars)
        adaptValueOnStack(gen, arg.`type`, asmType(ctor.superInitializer.arguments(i)))
        i += 1
      val superClass = if ctor.classType.superClass != null then AsmUtil.internalName(ctor.classType.superClass.name) else "java/lang/Object"
      val superArgTypes = ctor.superInitializer.arguments.map(asmType)
      gen.invokeConstructor(
        AsmUtil.objectType(superClass),
        AsmMethod("<init>", AsmType.getMethodDescriptor(AsmType.VOID_TYPE, superArgTypes*))
      )
    else
      gen.loadThis()
      gen.invokeConstructor(AsmUtil.objectType("java/lang/Object"), AsmMethod.getMethod("void <init>()"))

    if ctor.block != null then
      emitStatementsWithContext(gen, ctor.block.statements, className, localVars)
    else
      // Synthetic constructor for records: assign parameters to fields
      val classType = ctor.classType.asInstanceOf[ClassDefinition]
      val fields = classType.fields
      var argIndex = 0
      for field <- fields if (field.modifier & Modifier.STATIC) == 0 do
        gen.loadThis()
        gen.loadArg(argIndex)
        gen.putField(AsmUtil.objectType(className), field.name, asmType(field.`type`))
        argIndex += 1
    gen.returnValue()
    gen.endMethod()

  private def codeEnumClinit(
    cw: ClassWriter,
    classDef: ClassDefinition,
    className: String,
    staticInitializers: Array[ActionStatement]
  ): Unit =
    // Generate static initializer for enum
    val gen = MethodEmitter.newGenerator(cw, Opcodes.ACC_STATIC, "<clinit>", AsmType.VOID_TYPE, Array.empty)
    val enumType = AsmUtil.objectType(className)
    val stringType = AsmType.getType("Ljava/lang/String;")

    // Get enum constants (static final fields of the enum type)
    val enumFields = classDef.fields.filter { field =>
      Modifier.isStatic(field.modifier) && Modifier.isFinal(field.modifier) &&
      field.`type`.name == classDef.name
    }

    // Initialize each enum constant
    enumFields.zipWithIndex.foreach { case (field, ordinal) =>
      // new EnumType(name, ordinal)
      gen.newInstance(enumType)
      gen.dup()
      gen.push(field.name)  // name
      gen.push(ordinal)     // ordinal
      gen.invokeConstructor(enumType, AsmMethod("<init>", AsmType.getMethodDescriptor(AsmType.VOID_TYPE, stringType, AsmType.INT_TYPE)))
      gen.putStatic(enumType, field.name, enumType)
    }

    if staticInitializers.nonEmpty then
      val localVars = new LocalVarContext(gen).withParameters(true, Array.empty)
      emitStatementsWithContext(gen, staticInitializers, className, localVars)

    gen.returnValue()
    gen.endMethod()

  private def codeStaticInitializer(
    cw: ClassWriter,
    className: String,
    staticInitializers: Array[ActionStatement]
  ): Unit =
    val gen = MethodEmitter.newGenerator(cw, Opcodes.ACC_STATIC, "<clinit>", AsmType.VOID_TYPE, Array.empty)
    val localVars = new LocalVarContext(gen).withParameters(true, Array.empty)
    emitStatementsWithContext(gen, staticInitializers, className, localVars)
    gen.returnValue()
    gen.endMethod()

  private def codeInterfaceMethod(cw: ClassWriter, node: MethodDefinition, className: String): Unit =
    // Interface methods are abstract, so no method body
    var access = toAsmModifier(node.modifier) | Opcodes.ACC_ABSTRACT
    if (node.isVararg) access |= Opcodes.ACC_VARARGS
    val argTypes = node.arguments.map(asmType)
    val returnType = asmType(node.returnType)
    val desc = AsmType.getMethodDescriptor(returnType, argTypes*)

    // Convert throws types to internal names
    val exceptions = if (node.throwsTypes.isEmpty) null
                     else node.throwsTypes.map(t => AsmUtil.internalName(t.name))

    // Just visit the method without any code (abstract method needs visitEnd but no code)
    val mv = cw.visitMethod(access, node.name, desc, null, exceptions)
    mv.visitEnd()

  private def codeMethod(cw: ClassWriter, node: MethodDefinition, className: String): Unit =
    var access = toAsmModifier(node.modifier)
    if (node.isVararg) access |= Opcodes.ACC_VARARGS
    val argTypes = node.arguments.map(asmType)
    val returnType = asmType(node.returnType)
    val exceptions = if (node.throwsTypes.isEmpty) null
                     else node.throwsTypes.map(t => AsmUtil.internalName(t.name))
    val gen = MethodEmitter.newGenerator(cw, access, node.name, returnType, argTypes, exceptions)

    // Emit line number for method declaration
    if node.location != null then
      val label = gen.mark()
      gen.visitLineNumber(node.location.line, label)

    val isStatic = (node.modifier & Modifier.STATIC) != 0
    val localVars = new LocalVarContext(gen)
      .withParameters(isStatic, argTypes)
      .withBoxedVariables(node.getFrame)

    // TCO: Pre-allocate JVM slots for loop variables before emitting method body
    node.getTcoLoopVars match {
      case Some(loopVars) =>
        System.err.println(s"[CodeGen] Pre-allocating ${loopVars.length} TCO loop variable slots for ${node.name}")
        loopVars.foreach { case (loopVarIndex, paramType) =>
          val slot = localVars.allocateSlot(loopVarIndex, asmType(paramType))
          System.err.println(s"[CodeGen]   TypedAST index $loopVarIndex -> JVM slot $slot (${paramType.name})")
        }
      case None =>
        // Non-TCO method, no action needed
    }

    // Synthetic getter for records: non-abstract, no block, no args, has return type, and NOT static
    val isSyntheticGetter = node.block == null && !Modifier.isAbstract(node.modifier) &&
                            node.arguments.isEmpty && node.returnType != BasicType.VOID && !isStatic &&
                            !Modifier.isSyntheticRecord(node.modifier)

    // Check for synthetic record methods (equals, hashCode, toString, copy)
    val isSyntheticRecord = Modifier.isSyntheticRecord(node.modifier)

    if node.block != null then
      emitStatementsWithContext(gen, node.block.statements, className, localVars)
    else if isSyntheticRecord then
      // Generate synthetic record method bytecode
      node.classType match
        case classDef: ClassDefinition if classDef.isRecord =>
          val components = classDef.recordComponents.getOrElse(Array.empty[(String, Type)])
          node.name match
            case "equals"   => emitRecordEquals(gen, className, components)
            case "hashCode" => emitRecordHashCode(gen, className, components)
            case "toString" => emitRecordToString(gen, className, classDef.name, components)
            case "copy"     => emitRecordCopy(gen, className, components)
            case _ => // Unknown synthetic record method, no-op
        case _ => // Not a record, no-op
    else if isSyntheticGetter then
      // Synthetic getter for records: return this.fieldName
      gen.loadThis()
      gen.getField(AsmUtil.objectType(className), node.name, returnType)

    if !isSyntheticGetter && !isSyntheticRecord then
      val needsDefault = node.block == null || !hasReturn(node.block.statements)
      MethodEmitter.ensureReturn(gen, returnType, !needsDefault)
    else
      gen.returnValue()
    try gen.endMethod()
    catch
      case e: Throwable =>
        val desc = AsmType.getMethodDescriptor(returnType, argTypes*)
        throw new RuntimeException(s"Bytecode generation failed at $className.${node.name}$desc", e)
    
  private[compiler] def hasReturn(stmts: Array[ActionStatement]): Boolean =
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

  // =====================================================
  // Synthetic Record Methods: equals, hashCode, toString, copy
  // =====================================================

  private val ObjectsType: AsmType = AsmType.getType(classOf[java.util.Objects])
  private val ObjectsEquals: AsmMethod = AsmMethod.getMethod("boolean equals(Object, Object)")
  private val ObjectsHashCode: AsmMethod = AsmMethod.getMethod("int hashCode(Object)")
  private val StringBuilderType: AsmType = AsmType.getType(classOf[java.lang.StringBuilder])
  private val StringBuilderAppendString: AsmMethod = AsmMethod.getMethod("StringBuilder append(String)")
  private val StringBuilderAppendObject: AsmMethod = AsmMethod.getMethod("StringBuilder append(Object)")
  private val StringBuilderAppendInt: AsmMethod = AsmMethod.getMethod("StringBuilder append(int)")
  private val StringBuilderAppendLong: AsmMethod = AsmMethod.getMethod("StringBuilder append(long)")
  private val StringBuilderAppendDouble: AsmMethod = AsmMethod.getMethod("StringBuilder append(double)")
  private val StringBuilderAppendFloat: AsmMethod = AsmMethod.getMethod("StringBuilder append(float)")
  private val StringBuilderAppendBoolean: AsmMethod = AsmMethod.getMethod("StringBuilder append(boolean)")
  private val StringBuilderAppendChar: AsmMethod = AsmMethod.getMethod("StringBuilder append(char)")
  private val StringBuilderToString: AsmMethod = AsmMethod.getMethod("String toString()")
  private val StringBuilderInit: AsmMethod = AsmMethod.getMethod("void <init>(String)")

  /**
   * Generate equals method for record:
   * {{{
   * public boolean equals(Object other) {
   *   if (this == other) return true;
   *   if (other == null) return false;
   *   if (!(other instanceof ThisClass)) return false;
   *   ThisClass that = (ThisClass) other;
   *   return Objects.equals(this.field1, that.field1) && ...;
   * }
   * }}}
   */
  private def emitRecordEquals(gen: GeneratorAdapter, className: String, components: Array[(String, Type)]): Unit =
    val classType = AsmUtil.objectType(className)

    // if (this == other) return true
    gen.loadThis()
    gen.loadArg(0)
    val notSameRef = gen.newLabel()
    gen.ifCmp(AsmUtil.objectType(AsmUtil.JavaLangObject), GeneratorAdapter.NE, notSameRef)
    gen.push(true)
    gen.returnValue()
    gen.mark(notSameRef)

    // if (other == null) return false
    gen.loadArg(0)
    val notNull = gen.newLabel()
    gen.ifNonNull(notNull)
    gen.push(false)
    gen.returnValue()
    gen.mark(notNull)

    // if (!(other instanceof ThisClass)) return false
    gen.loadArg(0)
    gen.instanceOf(classType)
    val isInstance = gen.newLabel()
    gen.ifZCmp(GeneratorAdapter.NE, isInstance)
    gen.push(false)
    gen.returnValue()
    gen.mark(isInstance)

    // ThisClass that = (ThisClass) other
    gen.loadArg(0)
    gen.checkCast(classType)
    val thatLocal = gen.newLocal(classType)
    gen.storeLocal(thatLocal)

    // Compare each field
    for (name, fieldType) <- components do
      val fieldAsmType = asmType(fieldType)
      val fieldMatches = gen.newLabel()

      gen.loadThis()
      gen.getField(classType, name, fieldAsmType)
      gen.loadLocal(thatLocal)
      gen.getField(classType, name, fieldAsmType)

      if fieldType.isBasicType then
        // Primitive comparison
        gen.ifCmp(fieldAsmType, GeneratorAdapter.EQ, fieldMatches)
        gen.push(false)
        gen.returnValue()
        gen.mark(fieldMatches)
      else
        // Objects.equals(a, b)
        gen.invokeStatic(ObjectsType, ObjectsEquals)
        gen.ifZCmp(GeneratorAdapter.NE, fieldMatches)
        gen.push(false)
        gen.returnValue()
        gen.mark(fieldMatches)

    // All fields match
    gen.push(true)

  /**
   * Generate hashCode method for record:
   * {{{
   * public int hashCode() {
   *   int result = 1;
   *   result = 31 * result + Objects.hashCode(field1);
   *   result = 31 * result + field2; // for primitives
   *   ...
   *   return result;
   * }
   * }}}
   */
  private def emitRecordHashCode(gen: GeneratorAdapter, className: String, components: Array[(String, Type)]): Unit =
    val classType = AsmUtil.objectType(className)

    // int result = 1
    gen.push(1)
    val resultLocal = gen.newLocal(AsmType.INT_TYPE)
    gen.storeLocal(resultLocal)

    for (name, fieldType) <- components do
      val fieldAsmType = asmType(fieldType)

      // result = 31 * result + hashCode(field)
      gen.push(31)
      gen.loadLocal(resultLocal)
      gen.math(GeneratorAdapter.MUL, AsmType.INT_TYPE)

      gen.loadThis()
      gen.getField(classType, name, fieldAsmType)

      fieldType match
        case BasicType.INT =>
          // int hash is the value itself
        case BasicType.LONG =>
          // Long.hashCode(value) = (int)(value ^ (value >>> 32))
          gen.dup2()
          gen.push(32)
          gen.visitInsn(Opcodes.LUSHR)
          gen.visitInsn(Opcodes.LXOR)
          gen.cast(AsmType.LONG_TYPE, AsmType.INT_TYPE)
        case BasicType.DOUBLE =>
          // Double.doubleToLongBits then same as long
          gen.invokeStatic(AsmType.getType(classOf[java.lang.Double]), AsmMethod.getMethod("long doubleToLongBits(double)"))
          gen.dup2()
          gen.push(32)
          gen.visitInsn(Opcodes.LUSHR)
          gen.visitInsn(Opcodes.LXOR)
          gen.cast(AsmType.LONG_TYPE, AsmType.INT_TYPE)
        case BasicType.FLOAT =>
          // Float.floatToIntBits
          gen.invokeStatic(AsmType.getType(classOf[java.lang.Float]), AsmMethod.getMethod("int floatToIntBits(float)"))
        case BasicType.BOOLEAN =>
          // Boolean.hashCode(value) = value ? 1231 : 1237
          val trueLabel = gen.newLabel()
          val endLabel = gen.newLabel()
          gen.ifZCmp(GeneratorAdapter.NE, trueLabel)
          gen.push(1237)
          gen.goTo(endLabel)
          gen.mark(trueLabel)
          gen.push(1231)
          gen.mark(endLabel)
        case BasicType.BYTE | BasicType.SHORT | BasicType.CHAR =>
          // Cast to int
          gen.cast(fieldAsmType, AsmType.INT_TYPE)
        case _ =>
          // Objects.hashCode(obj)
          gen.invokeStatic(ObjectsType, ObjectsHashCode)

      gen.math(GeneratorAdapter.ADD, AsmType.INT_TYPE)
      gen.storeLocal(resultLocal)

    gen.loadLocal(resultLocal)

  /**
   * Generate toString method for record:
   * {{{
   * public String toString() {
   *   return "ClassName(field1=" + field1 + ", field2=" + field2 + ")";
   * }
   * }}}
   */
  private def emitRecordToString(gen: GeneratorAdapter, className: String, simpleName: String, components: Array[(String, Type)]): Unit =
    val classType = AsmUtil.objectType(className)
    val shortName = simpleName.split('.').last

    // new StringBuilder("ClassName(")
    gen.newInstance(StringBuilderType)
    gen.dup()
    gen.push(s"$shortName(")
    gen.invokeConstructor(StringBuilderType, StringBuilderInit)

    for i <- components.indices do
      val (name, fieldType) = components(i)
      val fieldAsmType = asmType(fieldType)

      // "fieldName="
      gen.push(s"$name=")
      gen.invokeVirtual(StringBuilderType, StringBuilderAppendString)

      // this.field
      gen.loadThis()
      gen.getField(classType, name, fieldAsmType)

      // append field value
      fieldType match
        case BasicType.INT | BasicType.BYTE | BasicType.SHORT =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendInt)
        case BasicType.LONG =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendLong)
        case BasicType.DOUBLE =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendDouble)
        case BasicType.FLOAT =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendFloat)
        case BasicType.BOOLEAN =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendBoolean)
        case BasicType.CHAR =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendChar)
        case _ =>
          gen.invokeVirtual(StringBuilderType, StringBuilderAppendObject)

      // ", " except for last
      if i < components.length - 1 then
        gen.push(", ")
        gen.invokeVirtual(StringBuilderType, StringBuilderAppendString)

    // ")"
    gen.push(")")
    gen.invokeVirtual(StringBuilderType, StringBuilderAppendString)

    // toString()
    gen.invokeVirtual(StringBuilderType, StringBuilderToString)

  /**
   * Generate copy method for record:
   * {{{
   * public ThisClass copy(T1 arg1, T2 arg2, ...) {
   *   return new ThisClass(arg1, arg2, ...);
   * }
   * }}}
   */
  private def emitRecordCopy(gen: GeneratorAdapter, className: String, components: Array[(String, Type)]): Unit =
    val classType = AsmUtil.objectType(className)
    val argTypes = components.map { case (_, tp) => asmType(tp) }

    // new ThisClass(arg0, arg1, ...)
    gen.newInstance(classType)
    gen.dup()

    for i <- components.indices do
      gen.loadArg(i)

    val ctorDesc = AsmType.getMethodDescriptor(AsmType.VOID_TYPE, argTypes*)
    gen.invokeConstructor(classType, AsmMethod("<init>", ctorDesc))

  private def emitStatements(gen: GeneratorAdapter, stmts: Array[ActionStatement], className: String): Unit =
    emitStatementsWithContext(gen, stmts, className, new LocalVarContext(gen))

  private def withVisitor(gen: GeneratorAdapter, className: String, localVars: LocalVarContext)(action: AsmCodeGenerationVisitor => Unit): Unit =
    val visitor = new AsmCodeGenerationVisitor(gen, className, localVars, this)
    action(visitor)

  private def emitStatementsWithContext(gen: GeneratorAdapter, stmts: Array[ActionStatement], className: String, localVars: LocalVarContext): Unit =
    withVisitor(gen, className, localVars)(visitor => stmts.foreach(visitor.visitStatement))

  private def emitStatement(gen: GeneratorAdapter, stmt: ActionStatement, className: String): Unit =
    emitStatementsWithContext(gen, Array(stmt), className, new LocalVarContext(gen))
    
  private[compiler] def emitStatementWithContext(gen: GeneratorAdapter, stmt: ActionStatement, className: String, localVars: LocalVarContext): Unit =
    emitStatementsWithContext(gen, Array(stmt), className, localVars)
    
  private def emitExpression(gen: GeneratorAdapter, expr: Term, className: String): Unit =
    emitExpressionWithContext(gen, expr, className, new LocalVarContext(gen))
    
  private def emitExpressionWithContext(gen: GeneratorAdapter, expr: Term, className: String, localVars: LocalVarContext): Unit =
    withVisitor(gen, className, localVars)(_.visitTerm(expr))


  // Helper methods for visitor pattern
  def emitRefLocal(gen: GeneratorAdapter, ref: RefLocal, localVars: LocalVarContext): Unit =
    localVars match
      case closureCtx: ClosureLocalVarContext =>
        // Use frameIndex to handle nested closures correctly
        closureCtx.capturedBinding(ref.frame, ref.index) match
          case Some(binding) =>
            gen.loadThis()
            val fieldType = if binding.isBoxed then boxAsmType(binding.tp) else asmType(binding.tp)
            gen.getField(
              AsmUtil.objectType(closureCtx.closureClassName),
              closureCtx.capturedFieldName(binding),
              fieldType
            )
            // If boxed, also get the value from the box
            if binding.isBoxed then
              gen.getField(boxAsmType(binding.tp), "value", boxedValueType(binding.tp))
          case None =>
            // For frame=0 (current closure's own variables/parameters)
            if ref.frame == 0 && closureCtx.isParameter(ref.index) then
              gen.loadArg(ref.index)
            else if ref.frame == 0 then
              val slot = closureCtx.slotOf(ref.index).getOrElse(closureCtx.getOrAllocateSlot(ref.index, asmType(ref.`type`)))
              gen.loadLocal(slot)
            else
              throw new IllegalStateException(s"Non-captured variable with frame=${ref.frame}, index=${ref.index} in closure context")
      case _ =>
        if localVars.isParameter(ref.index) then
          gen.loadArg(ref.index)
        else if localVars.isBoxed(ref.index) then
          // Boxed variable: load box, then get value field
          val boxType = boxAsmType(ref.`type`)
          val slot = localVars.slotOf(ref.index).getOrElse(localVars.getOrAllocateSlot(ref.index, boxType))
          gen.loadLocal(slot)
          gen.getField(boxType, "value", boxedValueType(ref.`type`))
        else
          val slot = localVars.slotOf(ref.index).getOrElse(localVars.getOrAllocateSlot(ref.index, asmType(ref.`type`)))
          gen.loadLocal(slot)
          
  def emitSetLocal(gen: GeneratorAdapter, set: SetLocal, className: String, localVars: LocalVarContext): Unit =
    localVars match
      case closureCtx: ClosureLocalVarContext =>
        // Use frameIndex to handle nested closures correctly
        closureCtx.capturedBinding(set.frame, set.index) match
          case Some(binding) if binding.isBoxed =>
            // Boxed captured variable: load box, compute value, put value into box
            gen.loadThis()
            val boxType = boxAsmType(binding.tp)
            gen.getField(
              AsmUtil.objectType(closureCtx.closureClassName),
              closureCtx.capturedFieldName(binding),
              boxType
            )
            emitExpressionWithContext(gen, set.value, className, localVars)
            val valueType = boxedValueType(set.`type`)
            if valueType.getSize() == 2 then
              gen.dup2X1()
            else
              gen.dupX1()
            gen.putField(boxType, "value", valueType)
          case Some(binding) =>
            gen.loadThis()
            emitExpressionWithContext(gen, set.value, className, localVars)
            val valueType = asmType(set.`type`)
            if valueType.getSize() == 2 then
              gen.dup2X1()
            else
              gen.dupX1()
            gen.putField(
              AsmUtil.objectType(closureCtx.closureClassName),
              closureCtx.capturedFieldName(binding),
              asmType(binding.tp)
            )
          case None =>
            // For frame=0 (current closure's own variables/parameters)
            if set.frame != 0 then
              throw new IllegalStateException(s"Non-captured variable with frame=${set.frame}, index=${set.index} in closure context")
            emitExpressionWithContext(gen, set.value, className, localVars)
            val valueType = asmType(set.`type`)
            if valueType.getSize() == 2 then gen.dup2() else gen.dup()
            if closureCtx.isParameter(set.index) then
              gen.storeArg(set.index)
            else
              val slot = closureCtx.slotOf(set.index).getOrElse(closureCtx.getOrAllocateSlot(set.index, valueType))
              gen.storeLocal(slot)
      case _ =>
        val setValueType = asmType(set.`type`)
        if localVars.isParameter(set.index) then
          emitExpressionWithContext(gen, set.value, className, localVars)
          if setValueType.getSize() == 2 then gen.dup2() else gen.dup()
          gen.storeArg(set.index)
        else if localVars.isBoxed(set.index) then
          val boxType = boxAsmType(set.`type`)
          val valueType = boxedValueType(set.`type`)
          localVars.slotOf(set.index) match
            case Some(slot) =>
              // Update: load box, compute value, put into box.value
              gen.loadLocal(slot)
              emitExpressionWithContext(gen, set.value, className, localVars)
              if valueType.getSize() == 2 then
                gen.dup2X1()
              else
                gen.dupX1()
              gen.putField(boxType, "value", valueType)
            case None =>
              // Initialize: compute value, create box, store box, return value
              emitExpressionWithContext(gen, set.value, className, localVars)
              val tempSlot = gen.newLocal(valueType)
              gen.storeLocal(tempSlot)
              gen.newInstance(boxType)
              gen.dup()
              gen.loadLocal(tempSlot)
              val ctorDesc = AsmType.getMethodDescriptor(AsmType.VOID_TYPE, valueType)
              gen.invokeConstructor(boxType, AsmMethod("<init>", ctorDesc))
              val slot = localVars.allocateSlot(set.index, boxType)
              gen.storeLocal(slot)
              gen.loadLocal(tempSlot) // Return the value
        else
          emitExpressionWithContext(gen, set.value, className, localVars)
          if setValueType.getSize() == 2 then gen.dup2() else gen.dup()
          val slot = localVars.slotOf(set.index).getOrElse(localVars.getOrAllocateSlot(set.index, setValueType))
          gen.storeLocal(slot)

  def emitNewClosure(gen: GeneratorAdapter, closure: NewClosure, className: String, localVars: LocalVarContext): Unit =
    closureCodegen.emitNewClosure(gen, closure, className, localVars)

/**
 * Companion object for static type conversion methods.
 * These are used by both AsmCodeGeneration and other packages (e.g., generics.Erasure).
 */
object AsmCodeGeneration:
  import org.objectweb.asm.commons.{Method => AsmMethod}
  import TypedAST._
  import bytecode.AsmUtil

  private[compiler] val LongBoxedType: AsmType = AsmType.getType(classOf[java.lang.Long])
  private[compiler] val DoubleBoxedType: AsmType = AsmType.getType(classOf[java.lang.Double])
  private[compiler] val LongValueOfMethod: AsmMethod = AsmMethod.getMethod("Long valueOf(long)")
  private[compiler] val DoubleValueOfMethod: AsmMethod = AsmMethod.getMethod("Double valueOf(double)")

  def asmType(tp: TypedAST.Type): AsmType = tp match
    case BasicType.VOID    => AsmType.VOID_TYPE
    case BasicType.BOOLEAN => AsmType.BOOLEAN_TYPE
    case BasicType.BYTE    => AsmType.BYTE_TYPE
    case BasicType.SHORT   => AsmType.SHORT_TYPE
    case BasicType.CHAR    => AsmType.CHAR_TYPE
    case BasicType.INT     => AsmType.INT_TYPE
    case BasicType.LONG    => AsmType.LONG_TYPE
    case BasicType.FLOAT   => AsmType.FLOAT_TYPE
    case BasicType.DOUBLE  => AsmType.DOUBLE_TYPE
    case tv: TypeVariableType => asmType(tv.upperBound)
    case ap: AppliedClassType => asmType(ap.raw)
    case w: WildcardType      => asmType(w.upperBound)
    case nt: NullableType     =>
      // NullableType at runtime uses the boxed form for primitives, same type for objects
      nt.innerType match
        case BasicType.VOID    => AsmUtil.objectType(AsmUtil.JavaLangObject)
        case BasicType.BOOLEAN => AsmUtil.objectType("java.lang.Boolean")
        case BasicType.BYTE    => AsmUtil.objectType("java.lang.Byte")
        case BasicType.SHORT   => AsmUtil.objectType("java.lang.Short")
        case BasicType.CHAR    => AsmUtil.objectType("java.lang.Character")
        case BasicType.INT     => AsmUtil.objectType("java.lang.Integer")
        case BasicType.LONG    => AsmUtil.objectType("java.lang.Long")
        case BasicType.FLOAT   => AsmUtil.objectType("java.lang.Float")
        case BasicType.DOUBLE  => AsmUtil.objectType("java.lang.Double")
        case other => asmType(other)
    case ct: ClassType     => AsmUtil.objectType(ct.name)
    case at: ArrayType     =>
      // Build descriptor with correct number of dimensions
      val componentDescriptor = asmType(at.component).getDescriptor
      AsmType.getType("[" * at.dimension + componentDescriptor)
    case _: NullType       => AsmUtil.objectType(AsmUtil.JavaLangObject)
    case _: BottomType     => AsmType.VOID_TYPE
    case _                 => throw new RuntimeException(s"Unsupported type: $tp")

  /**
   * Get the box class name for a given type (for boxed mutable variables)
   */
  def boxClassName(tp: TypedAST.Type): String = tp match
    case BasicType.INT     => "onion/runtime/IntBox"
    case BasicType.LONG    => "onion/runtime/LongBox"
    case BasicType.DOUBLE  => "onion/runtime/DoubleBox"
    case BasicType.FLOAT   => "onion/runtime/FloatBox"
    case _                 => "onion/runtime/ObjectBox"

  def boxAsmType(tp: TypedAST.Type): AsmType =
    AsmType.getObjectType(boxClassName(tp))
