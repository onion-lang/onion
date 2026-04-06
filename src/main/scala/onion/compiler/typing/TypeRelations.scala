package onion.compiler.typing

import onion.compiler.ClassTable
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

private[typing] object TypeRelations {
  def sameClass(left: ClassType, right: ClassType): Boolean =
    (left eq right) || (left.name == right.name)

  def isAssignableWithBoxing(expected: Type, actual: Type, table: ClassTable): Boolean =
    if TypeRules.isAssignable(expected, actual) then true
    else if !expected.isBasicType && actual.isBasicType then
      val basicType = actual.asInstanceOf[BasicType]
      basicType != BasicType.VOID && TypeRules.isAssignable(expected, Boxing.boxedType(table, basicType))
    else if expected.isBasicType && !actual.isBasicType then
      val basicType = expected.asInstanceOf[BasicType]
      basicType != BasicType.VOID && TypeRules.isAssignable(Boxing.boxedType(table, basicType), actual)
    else
      (expected, actual) match
        case (expectedApplied: AppliedClassType, actualApplied: AppliedClassType) =>
          sameClass(expectedApplied.raw, actualApplied.raw) &&
            expectedApplied.typeArguments.length == actualApplied.typeArguments.length &&
            expectedApplied.typeArguments.zip(actualApplied.typeArguments).forall { (expArg, actArg) =>
              isAssignableWithBoxing(expArg, actArg, table)
            }
        case _ =>
          false
}
