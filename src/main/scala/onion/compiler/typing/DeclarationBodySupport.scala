package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingUnitContext

import scala.collection.mutable.Buffer

private[compiler] final class DeclarationBodySupport(
  typing: Typing,
  unitContext: TypingUnitContext,
  classInitializerSupport: ClassInitializerSupport,
  methodBodySupport: MethodBodySupport,
  processMethodDeclaration: AST.MethodDeclaration => Unit,
  processConstructorDeclaration: AST.ConstructorDeclaration => Unit
) {
  def processClassDeclaration(node: AST.ClassDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    typing.find(definition.name).foreach(unitContext.currentMapper = _)
    val classTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
    typing.openTypeParams(typing.emptyTypeParams ++ classTypeParams) {
      val instanceInitializers = Buffer[ActionStatement]()
      val staticInitializers = Buffer[ActionStatement]()
      for (section <- node.defaultSection ++ node.sections; member <- section.members) {
        member match {
          case field: AST.FieldDeclaration =>
            classInitializerSupport.collectFieldInitializer(field, definition, instanceInitializers, staticInitializers)
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case field: AST.DelegatedFieldDeclaration =>
            classInitializerSupport.collectDelegatedFieldInitializer(field, definition, instanceInitializers, staticInitializers)
        }
      }
      classInitializerSupport.injectInstanceInitializers(definition, instanceInitializers.toSeq)
      if (staticInitializers.nonEmpty) {
        definition.setStaticInitializers(staticInitializers.toArray)
      }
    }
  }

  def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    typing.find(definition.name).foreach(unitContext.currentMapper = _)

    val receiverType = typing.mapFrom(node.receiverType)
    if (receiverType != null) {
      for (methodNode <- node.methods) {
        methodBodySupport.processExtensionMethodDeclaration(methodNode, receiverType, definition)
      }
    }
  }
}
