package onion.compiler.generics

import onion.compiler.{AsmCodeGeneration, TypedAST}
import org.objectweb.asm.{Type => AsmType}

/**
  * Erasure helpers for the Onion generics implementation.
  *
  * Onion uses an erasure-based model on the JVM, so type variables and type applications
  * must be erased consistently across typing and bytecode generation.
  */
object Erasure {
  /**
    * Compute the erased ASM type for a TypedAST type.
    * Delegates to the canonical implementation in AsmCodeGeneration.
    */
  def asmType(tp: TypedAST.Type): AsmType = AsmCodeGeneration.asmType(tp)

  def methodDescriptor(ret: TypedAST.Type, args: Array[TypedAST.Type]): String =
    AsmType.getMethodDescriptor(asmType(ret), args.map(asmType)*)
}
