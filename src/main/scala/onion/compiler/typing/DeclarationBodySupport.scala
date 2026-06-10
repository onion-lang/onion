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
  def processClassDeclaration(node: AST.ClassDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    typing.setDefinition(definition)
    typing.find(definition.name).foreach(typing.setMapper)
    val classTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
    typing.openTypeParams(typing.emptyTypeParams ++ classTypeParams) {
      val instanceInitializers = Buffer[ActionStatement]()
      val staticInitializers = Buffer[ActionStatement]()
      for (section <- node.defaultSection ++ node.sections; member <- section.members) {
        member match {
          case field: AST.FieldDeclaration =>
            classInitializerSupport.collectFieldInitializer(field, typing.definition_, instanceInitializers, staticInitializers)
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case field: AST.DelegatedFieldDeclaration =>
            classInitializerSupport.collectDelegatedFieldInitializer(field, typing.definition_, instanceInitializers, staticInitializers)
        }
      }
      classInitializerSupport.injectInstanceInitializers(typing.definition_, instanceInitializers.toSeq)
      if (staticInitializers.nonEmpty) {
        typing.definition_.setStaticInitializers(staticInitializers.toArray)
      }
    }
  }

  def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    typing.setDefinition(definition)
    typing.find(definition.name).foreach(typing.setMapper)

    val receiverType = typing.mapFrom(node.receiverType)
    if (receiverType != null) {
      for (methodNode <- node.methods) {
        methodBodySupport.processExtensionMethodDeclaration(methodNode, receiverType, definition)
      }
    }
  }
}
