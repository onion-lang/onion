package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import scala.collection.mutable.Buffer

private[compiler] final class DeclarationBodySupport(
  typing: Typing,
  classInitializerSupport: ClassInitializerSupport,
  methodBodySupport: MethodBodySupport,
  processMethodDeclaration: AST.MethodDeclaration => Unit,
  processConstructorDeclaration: AST.ConstructorDeclaration => Unit
) {
  import typing.*

  def processClassDeclaration(node: AST.ClassDeclaration): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)
    val classTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(emptyTypeParams ++ classTypeParams) {
      val instanceInitializers = Buffer[ActionStatement]()
      val staticInitializers = Buffer[ActionStatement]()
      for (section <- node.defaultSection ++ node.sections; member <- section.members) {
        member match {
          case field: AST.FieldDeclaration =>
            classInitializerSupport.collectFieldInitializer(field, definition_, instanceInitializers, staticInitializers)
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case field: AST.DelegatedFieldDeclaration =>
            classInitializerSupport.collectDelegatedFieldInitializer(field, definition_, instanceInitializers, staticInitializers)
        }
      }
      classInitializerSupport.injectInstanceInitializers(definition_, instanceInitializers.toSeq)
      if (staticInitializers.nonEmpty) {
        definition_.setStaticInitializers(staticInitializers.toArray)
      }
    }
  }

  def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    if (definition_ == null) return
    mapper_ = find(definition_.name)

    val receiverType = mapFrom(node.receiverType)
    if (receiverType == null) return

    for (methodNode <- node.methods) {
      methodBodySupport.processExtensionMethodDeclaration(methodNode, receiverType, definition_)
    }
  }
}
