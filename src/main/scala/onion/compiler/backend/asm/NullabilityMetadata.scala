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

  /**
   * Marks a type-class dictionary parameter: one of the trailing parameters a `[T: Trait]`
   * method carries, one per (type parameter, trait) in declaration order (#2768). A separate
   * compilation reads the constraint back from it and fills the slot at each call, as it does
   * within one compilation.
   */
  val DictionaryDescriptor: String = "Lonion/internal/Dictionary;"

  def emitParameters(mv: MethodVisitor, arguments: Array[Type], dictionaryCount: Int = 0): Unit =
    if arguments != null && (dictionaryCount > 0 || arguments.exists(isMarked)) then
      mv.visitAnnotableParameterCount(arguments.length, false)
      val firstDictionary = arguments.length - dictionaryCount
      for (arg, i) <- arguments.zipWithIndex do
        if isMarked(arg) then mv.visitParameterAnnotation(i, NullableDescriptor, false).visitEnd()
        if i >= firstDictionary then mv.visitParameterAnnotation(i, DictionaryDescriptor, false).visitEnd()
