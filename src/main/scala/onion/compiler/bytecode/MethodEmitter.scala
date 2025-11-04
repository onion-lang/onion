package onion.compiler.bytecode

import org.objectweb.asm.{ClassWriter, Type as AsmType}
import org.objectweb.asm.commons.{GeneratorAdapter, Method as AsmMethod}

object MethodEmitter {
  def newGenerator(
    cw: ClassWriter,
    access: Int,
    name: String,
    returnType: AsmType,
    argTypes: Array[AsmType]
  ): GeneratorAdapter = {
    val desc = AsmType.getMethodDescriptor(returnType, argTypes*)
    val mv = cw.visitMethod(access, name, desc, null, null)
    val asmMethod = AsmMethod(name, desc)
    val gen = new GeneratorAdapter(access, asmMethod, mv)
    gen.visitCode()
    gen
  }

  def ensureReturn(gen: GeneratorAdapter, returnType: AsmType, hasReturn: Boolean): Unit = {
    if (!hasReturn) AsmUtil.emitDefaultReturn(gen, returnType)
  }
}

