package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class AssignabilitySupport(
  typing: Typing,
  bodyContext: TypingBodyContext
) {

  def processAssignable(node: AST.Node, expected: Type, actual: Term): Term = {
    if (actual == null) return null
    if (actual.`type`.isBottomType) return actual
    if (expected == actual.`type`) return actual

    if (!expected.isBasicType && actual.`type`.isBasicType) {
      val basicType = actual.`type`.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) {
        bodyContext.report(IS_NOT_BOXABLE_TYPE, node, basicType)
        return null
      }
      val boxed = Boxing.boxing(bodyContext.table, actual)
      if (TypeRules.isAssignable(expected, boxed.`type`)) {
        return if (expected == boxed.`type`) boxed else new AsInstanceOf(node.location, boxed, expected)
      }
    }

    if (expected.isBasicType && !actual.`type`.isBasicType) {
      val targetBasicType = expected.asInstanceOf[BasicType]
      if (targetBasicType == BasicType.VOID) {
        bodyContext.report(INCOMPATIBLE_TYPE, node, expected, actual.`type`)
        return null
      }
      val boxedType = Boxing.boxedType(bodyContext.table, targetBasicType)
      if (TypeRules.isAssignable(boxedType, actual.`type`)) {
        return Boxing.unboxing(bodyContext.table, actual, targetBasicType)
      }
    }

    def containsTypeVariable(typeToCheck: Type): Boolean =
      TypeCheckingHelpers.containsTypeVariable(typeToCheck)

    def structurallyAssignable(expected: Type, actual: Type): Boolean = (expected, actual) match {
      case (tv: TypedAST.TypeVariableType, _) =>
        TypeRules.isSuperType(tv.upperBound, actual)
      case (ae: TypedAST.AppliedClassType, aa: TypedAST.AppliedClassType) =>
        TypeRelations.sameClass(ae.raw, aa.raw) &&
          ae.typeArguments.length == aa.typeArguments.length &&
          ae.typeArguments.zip(aa.typeArguments).forall { case (e, a) => structurallyAssignable(e, a) }
      case (ae: TypedAST.AppliedClassType, _) if containsTypeVariable(ae) =>
        actual match {
          case classType: TypedAST.ClassType => TypeRelations.sameClass(ae.raw, classType)
          case _ => false
        }
      case _ =>
        TypeRules.isAssignable(expected, actual)
    }

    val isCompatible =
      if (containsTypeVariable(expected)) structurallyAssignable(expected, actual.`type`)
      else TypeRelations.isAssignableWithBoxing(expected, actual.`type`, bodyContext.table)

    if (!isCompatible) {
      bodyContext.report(INCOMPATIBLE_TYPE, node, expected, actual.`type`)
      return null
    }
    new AsInstanceOf(node.location, actual, expected)
  }
}
