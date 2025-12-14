package onion.compiler.generics

import onion.compiler.TypedAST
import onion.compiler.bytecode.AsmUtil
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
    *
    * - `TypeVariableType` erases to its upper bound.
    * - `AppliedClassType` erases to its raw class.
    * - `null` erases to `Object`.
    */
  def asmType(tp: TypedAST.Type): AsmType = tp match
    case TypedAST.BasicType.VOID    => AsmType.VOID_TYPE
    case TypedAST.BasicType.BOOLEAN => AsmType.BOOLEAN_TYPE
    case TypedAST.BasicType.BYTE    => AsmType.BYTE_TYPE
    case TypedAST.BasicType.SHORT   => AsmType.SHORT_TYPE
    case TypedAST.BasicType.CHAR    => AsmType.CHAR_TYPE
    case TypedAST.BasicType.INT     => AsmType.INT_TYPE
    case TypedAST.BasicType.LONG    => AsmType.LONG_TYPE
    case TypedAST.BasicType.FLOAT   => AsmType.FLOAT_TYPE
    case TypedAST.BasicType.DOUBLE  => AsmType.DOUBLE_TYPE
    case tv: TypedAST.TypeVariableType => asmType(tv.upperBound)
    case ap: TypedAST.AppliedClassType => asmType(ap.raw)
    case ct: TypedAST.ClassType => AsmUtil.objectType(ct.name)
    case at: TypedAST.ArrayType => AsmType.getType("[" * at.dimension + asmType(at.component).getDescriptor)
    case _: TypedAST.NullType   => AsmUtil.objectType(AsmUtil.JavaLangObject)
    case other => throw new RuntimeException(s"Unsupported type for erasure: $other")

  def methodDescriptor(ret: TypedAST.Type, args: Array[TypedAST.Type]): String =
    AsmType.getMethodDescriptor(asmType(ret), args.map(asmType)*)
}
