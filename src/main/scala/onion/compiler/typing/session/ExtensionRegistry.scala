package onion.compiler.typing.session

import onion.compiler.AST
import onion.compiler.TypedAST.{ClassDefinition, ExtensionMethodDefinition}

import scala.collection.mutable.{Buffer, HashMap}

final class ExtensionRegistry {
  private val declarations = Buffer[(AST.ExtensionDeclaration, ClassDefinition)]()
  private val methods = HashMap[String, Buffer[ExtensionMethodDefinition]]()

  def registerDeclaration(declaration: AST.ExtensionDeclaration, container: ClassDefinition): Unit =
    declarations += ((declaration, container))

  def registerMethod(receiverFqcn: String, method: ExtensionMethodDefinition): Unit =
    methods.getOrElseUpdate(receiverFqcn, Buffer()) += method

  /**
   * User-declared extensions first, then the process-wide builtin layer. The builtins
   * (Colls, Iterables, Maps, ...) are the same for every compilation, since their classes
   * come from the shared class table; computing them once and reading them here replaces a
   * reflective scan of ten stdlib classes that used to run on every compile that touched
   * an extension. Ordering is unchanged: builtins were appended lazily after the outline
   * pass had registered user methods, so user methods came first before too.
   */
  def methodsFor(receiverFqcn: String): Seq[ExtensionMethodDefinition] =
    val user = methods.get(receiverFqcn)
    val builtin = onion.compiler.Typing.builtinExtensions.getOrElse(receiverFqcn, Nil)
    user match
      case Some(buffer) if builtin.isEmpty => buffer.toSeq
      case Some(buffer) => buffer.toSeq ++ builtin
      case None => builtin
}
