package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[compiler] final class ExtensionMethodFallbackSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  import typing.*

  def tryExtensionMethodCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type
  ): Option[Term] = {
    selectApplicableExtensionMethod(targetType, node.name, params) match {
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousSignature(
          node,
          first.containerClass,
          node.name,
          first.arguments,
          second.containerClass,
          node.name,
          second.arguments
        )
        None
      case CandidateSelection.Selected(extMethod) =>
        buildExtensionCall(node, target, targetType, params, expected, extMethod)
    }
  }

  private def selectApplicableExtensionMethod(
    targetType: ObjectType,
    name: String,
    params: Array[Term]
  ): CandidateSelection[ExtensionMethodDefinition] =
    targetType match {
      case _: ClassType =>
        val applicable = collectExtensionMethods(targetType, name).filter { extMethod =>
          extMethod.name == name && isExtensionMethodApplicable(extMethod, params)
        }
        if (applicable.isEmpty) CandidateSelection.NoMatch
        else if (applicable.length > 1) CandidateSelection.Ambiguous(applicable(0), applicable(1))
        else CandidateSelection.Selected(applicable.head)
      case _ =>
        CandidateSelection.NoMatch
    }

  private def buildExtensionCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    extMethod: ExtensionMethodDefinition
  ): Option[Term] = {
    val containerClass = extMethod.containerClass
    val staticArgs = Array(target) ++ params
    val staticMethods = containerClass.findMethod(node.name, staticArgs)

    staticMethods match {
      case Array() =>
        calls.reportMethodNotFound(node, targetType, node.name, calls.types(params))
        None
      case Array(staticMethod) =>
        val classSubst = TypeSubstitution.classSubstitution(containerClass)
        calls.buildResolvedCall(node, staticMethod, staticArgs, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.processParamsWithExpected(node, staticArgs, expectedArgs),
          finalParams => new CallStatic(containerClass, staticMethod, finalParams)
        )
      case multiple =>
        calls.reportAmbiguousMethods(node, node.name, multiple)
        None
    }
  }

  private def collectExtensionMethods(
    targetType: ObjectType,
    name: String
  ): Seq[ExtensionMethodDefinition] = {
    val result = scala.collection.mutable.LinkedHashSet.empty[ExtensionMethodDefinition]

    def collect(currentType: ObjectType): Unit = {
      if (currentType == null) return
      currentType match {
        case classType: ClassType =>
          lookupExtensionMethods(classType.name).foreach { extMethod =>
            if (extMethod.name == name) result += extMethod
          }
        case _ =>
      }
      collect(currentType.superClass)
      currentType.interfaces.foreach(collect)
    }

    collect(targetType)
    result.toSeq
  }

  private def isExtensionMethodApplicable(
    extMethod: ExtensionMethodDefinition,
    params: Array[Term]
  ): Boolean = {
    val expectedArgs = extMethod.arguments
    params.length == expectedArgs.length &&
    params.indices.forall { i =>
      calls.isAssignableWithBoxing(expectedArgs(i), params(i).`type`)
    }
  }
}
