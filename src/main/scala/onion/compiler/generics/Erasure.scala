package onion.compiler.generics

import onion.compiler.TypedAST
import onion.compiler.backend.asm.AsmBackend
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
    * Delegates to the canonical implementation exposed through the ASM backend boundary.
    */
  def asmType(tp: TypedAST.Type): AsmType = AsmBackend.asmType(tp)

  def methodDescriptor(ret: TypedAST.Type, args: Array[TypedAST.Type]): String =
    AsmType.getMethodDescriptor(asmType(ret), args.map(asmType)*)
}
