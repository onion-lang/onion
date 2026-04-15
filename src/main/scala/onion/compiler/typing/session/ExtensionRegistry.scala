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

  def methodsFor(receiverFqcn: String): Seq[ExtensionMethodDefinition] =
    methods.getOrElse(receiverFqcn, Seq.empty).toSeq
}
