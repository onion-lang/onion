package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

/**
 * W0020: an `extension` method whose name and arity exactly match an existing member
 * (own or inherited) of its receiver type. Normal member resolution always wins over an
 * extension method (ExtensionMethodFallbackSupport only runs once ordinary resolution has
 * found nothing), so such an extension method compiles clean but is never called -- neither
 * a compile error nor a runtime one, just silent dead code (issue #2205).
 */
private[typing] object ExtensionShadowCheck {
  def check(typing: Typing, node: AST.Node, method: ExtensionMethodDefinition): Unit = {
    if (!typing.reportingEnabled) return
    receiverObjectType(typing, method.receiverType).foreach { receiver =>
      val arity = method.arguments.length
      val shadowedBy = MethodResolution.candidatesOf(receiver, method.name, typing.table_).find { existing =>
        !Modifier.isStatic(existing.modifier) &&
        existing.arguments.length == arity &&
        existing.arguments.indices.forall(i => paramAssignable(typing, existing.arguments(i), method.arguments(i)))
      }
      shadowedBy.foreach { _ =>
        typing.warningReporter_.setSourceFile(typing.unit_.sourceFile)
        typing.warningReporter_.extensionMethodShadowed(node.location, receiverDisplayName(receiver), method.name, arity)
      }
    }
  }

  private def receiverObjectType(typing: Typing, receiverType: Type): Option[ObjectType] =
    receiverType match {
      case ct: ClassType => Some(ct)
      case bt: BasicType if bt != BasicType.VOID => Some(Boxing.boxedType(typing.table_, bt))
      case _ => None // array receivers: builtin array "methods" are not real declared members, nothing to shadow
    }

  private def receiverDisplayName(receiver: ObjectType): String =
    receiver match {
      case ct: ClassType => ct.name
      case other => other.toString
    }

  /** Mirrors DuplicationChecks.primitiveAwareAssignable: would a value of the extension's
   *  declared parameter type satisfy the existing member's formal parameter, including a
   *  primitive/boxed crossing (the extension's param is typed before this check runs, so
   *  only the declared types -- not a real call site's argument types -- are compared). */
  private def paramAssignable(typing: Typing, existingParam: Type, extensionParam: Type): Boolean = {
    if (TypeRules.isAssignable(existingParam, extensionParam)) return true
    (existingParam, extensionParam) match {
      case (bt: BasicType, ct: ClassType) if bt != BasicType.VOID && typing.boxedTypeArgument(bt).name == ct.name => true
      case (ct: ClassType, bt: BasicType) if bt != BasicType.VOID && typing.boxedTypeArgument(bt).name == ct.name => true
      case _ => false
    }
  }
}
