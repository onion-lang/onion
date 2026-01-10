package onion.compiler.bytecode

import onion.compiler.*
import onion.compiler.TypedAST.*
import org.objectweb.asm.{ClassWriter, Opcodes, Type as AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method as AsmMethod}

final class ClosureCodegen(
  asmCodeGen: AsmCodeGeneration,
  outputDirectory: String,
  nextClosureId: () => Int,
  registerCompiledClass: CompiledClass => Unit
) {
  private def asmType(tp: TypedAST.Type): AsmType = asmCodeGen.asmType(tp)
  private def boxAsmType(tp: TypedAST.Type): AsmType = asmCodeGen.boxAsmType(tp)

  private def capturedFieldType(capturedVar: ClosureLocalBinding): AsmType =
    if capturedVar.isBoxed then boxAsmType(capturedVar.tp) else asmType(capturedVar.tp)

  def emitNewClosure(gen: GeneratorAdapter, closure: NewClosure, className: String, localVars: LocalVarContext): Unit = {
    val closureClassName = s"${className}$$Closure${nextClosureId()}"
    val interfaceType = closure.`type`

    // Capture outer-scope locals actually referenced by the closure body.
    // (Do not capture the closure's own parameters/locals.)
    val rawCapturedVars = CapturedVariableCollector.collect(closure.block, closure.frame)

    // Correct the isBoxed flags using localVars (which has the correct info from parent scope)
    val capturedVars = rawCapturedVars.map { v =>
      val isBoxed = localVars.isBoxed(v.index)
      new ClosureLocalBinding(v.frameIndex, v.index, v.tp, v.isMutable, isBoxed)
    }

    val closureBytes = generateClosureClass(closureClassName, interfaceType, closure.method, closure.block, capturedVars)
    registerCompiledClass(CompiledClass(closureClassName.replace('/', '.'), outputDirectory, closureBytes))

    val closureType = AsmUtil.objectType(closureClassName)
    gen.newInstance(closureType)
    gen.dup()

    for capturedVar <- capturedVars do
      if capturedVar.isBoxed then
        // For boxed variables, load the box object itself (not the value inside)
        val boxType = boxAsmType(capturedVar.tp)
        localVars match
          case closureCtx: ClosureLocalVarContext =>
            closureCtx.capturedBinding(capturedVar.index) match
              case Some(binding) =>
                gen.loadThis()
                gen.getField(
                  AsmUtil.objectType(closureCtx.closureClassName),
                  closureCtx.capturedFieldName(binding.index),
                  boxType
                )
              case None =>
                val slot = localVars.slotOf(capturedVar.index).getOrElse(
                  throw new IllegalStateException(s"Boxed variable ${capturedVar.index} not found")
                )
                gen.loadLocal(slot)
          case _ =>
            val slot = localVars.slotOf(capturedVar.index).getOrElse(
              throw new IllegalStateException(s"Boxed variable ${capturedVar.index} not found")
            )
            gen.loadLocal(slot)
      else
        // Use the correct frame from ClosureLocalBinding
        val ref = new RefLocal(capturedVar.frameIndex, capturedVar.index, capturedVar.tp)
        asmCodeGen.emitRefLocal(gen, ref, localVars)

    val ctorDesc = AsmType.getMethodDescriptor(
      AsmType.VOID_TYPE,
      capturedVars.map(capturedFieldType).toArray*
    )

    gen.visitMethodInsn(
      Opcodes.INVOKESPECIAL,
      closureClassName,
      "<init>",
      ctorDesc,
      false
    )
  }

  private def generateClosureClass(
    className: String,
    interfaceType: ClassType,
    method: TypedAST.Method,
    block: ActionStatement,
    capturedVars: Seq[ClosureLocalBinding]
  ): Array[Byte] = {
    val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)

    cw.visit(
      Opcodes.V17,
      Opcodes.ACC_PUBLIC,
      className,
      null,
      AsmUtil.internalName(AsmUtil.JavaLangObject),
      Array(AsmUtil.internalName(interfaceType.name))
    )

    for capturedVar <- capturedVars do
      val fieldType = capturedFieldType(capturedVar)
      cw.visitField(
        Opcodes.ACC_PRIVATE,
        s"captured_${capturedVar.index}",
        fieldType.getDescriptor,
        null,
        null
      )

    generateClosureConstructor(cw, className, capturedVars)
    generateClosureMethod(cw, className, method, block, capturedVars)

    cw.visitEnd()
    cw.toByteArray
  }

  private def generateClosureConstructor(cw: ClassWriter, className: String, capturedVars: Seq[ClosureLocalBinding]): Unit = {
    val ctorDesc = AsmType.getMethodDescriptor(
      AsmType.VOID_TYPE,
      capturedVars.map(capturedFieldType).toArray*
    )

    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc, null, null)
    val gen = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, "<init>", ctorDesc)

    gen.visitCode()
    gen.loadThis()
    gen.invokeConstructor(AsmUtil.objectType(AsmUtil.JavaLangObject), AsmMethod.getMethod("void <init>()"))

    for (capturedVar, paramIndex) <- capturedVars.zipWithIndex do
      gen.loadThis()
      gen.loadArg(paramIndex)
      gen.putField(
        AsmUtil.objectType(className),
        s"captured_${capturedVar.index}",
        capturedFieldType(capturedVar)
      )

    gen.returnValue()
    gen.endMethod()
  }

  private def generateClosureMethod(
    cw: ClassWriter,
    className: String,
    method: TypedAST.Method,
    block: ActionStatement,
    capturedVars: Seq[ClosureLocalBinding]
  ): Unit = {
    val argTypes = method.arguments.map(asmType)
    val returnType = asmType(method.returnType)

    val gen = MethodEmitter.newGenerator(
      cw,
      Opcodes.ACC_PUBLIC,
      method.name,
      returnType,
      argTypes
    )

    val closureLocalVars = new ClosureLocalVarContext(gen, className, capturedVars)
      .withParameters(isStatic = false, argTypes)

    asmCodeGen.emitStatementWithContext(gen, block, className, closureLocalVars)

    val needsDefault = method.returnType != BasicType.VOID && !asmCodeGen.hasReturn(Array(block))
    MethodEmitter.ensureReturn(gen, returnType, !needsDefault)
    gen.endMethod()
  }
}

