package onion.compiler.backend.asm

import onion.compiler.TypedAST.{BasicType, NullableType, Type}
import org.objectweb.asm.MethodVisitor

/**
 * Records which parameters were declared nullable (`T?`) so that a later, separate compilation
 * sees them as nullable too. A JVM descriptor or generic signature cannot say so; without this a
 * caller in another compilation unit (a project's `tests/` against `src/`) sees plain `T` and is
 * refused a `T?` argument.
 *
 * Each nullable reference-typed parameter carries a class-retention marker annotation. The
 * annotation class need not exist: nothing loads it at run time, and
 * [[onion.compiler.environment.AsmRefs]] reads it back by descriptor.
 */
object NullabilityMetadata:
  val NullableDescriptor: String = "Lonion/internal/Nullable;"

  private def isMarked(tp: Type): Boolean = tp match
    case nt: NullableType => !nt.innerType.isInstanceOf[BasicType]
    case _                => false

  def emitParameters(mv: MethodVisitor, arguments: Array[Type]): Unit =
    if arguments != null && arguments.exists(isMarked) then
      mv.visitAnnotableParameterCount(arguments.length, false)
      for (arg, i) <- arguments.zipWithIndex if isMarked(arg) do
        mv.visitParameterAnnotation(i, NullableDescriptor, false).visitEnd()
