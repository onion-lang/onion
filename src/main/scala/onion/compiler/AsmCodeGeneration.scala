package onion.compiler

import onion.compiler.backend.asm.{AsmCodeGeneration as BackendAsmCodeGeneration}
import org.objectweb.asm.{Type => AsmType}
import org.objectweb.asm.commons.{Method => AsmMethod}

/**
 * Public compatibility facade for the legacy `AsmCodeGeneration` entry point.
 *
 * The implementation now lives under `onion.compiler.backend.asm`, while
 * pipeline-facing code should depend on `AsmBackend`.
 */
class AsmCodeGeneration(config: CompilerConfig) extends BackendAsmCodeGeneration(config)

object AsmCodeGeneration:
  private[compiler] val LongBoxedType: AsmType = BackendAsmCodeGeneration.LongBoxedType
  private[compiler] val DoubleBoxedType: AsmType = BackendAsmCodeGeneration.DoubleBoxedType
  private[compiler] val LongValueOfMethod: AsmMethod = BackendAsmCodeGeneration.LongValueOfMethod
  private[compiler] val DoubleValueOfMethod: AsmMethod = BackendAsmCodeGeneration.DoubleValueOfMethod

  def asmType(tp: TypedAST.Type): AsmType =
    BackendAsmCodeGeneration.asmType(tp)

  def boxClassName(tp: TypedAST.Type): String =
    BackendAsmCodeGeneration.boxClassName(tp)

  def boxAsmType(tp: TypedAST.Type): AsmType =
    BackendAsmCodeGeneration.boxAsmType(tp)
