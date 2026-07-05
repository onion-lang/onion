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
        // Strip matching nullable wrappers so a nullable applied generic
        // (e.g. `Node[T]?`) is matched by the invariant AppliedClassType rule
        // below; without this a `Node[T]?` argument did not match a `Node[T]?`
        // parameter, since TypeRules.isAssignable alone cannot relate a
        // type-variable-parameterized applied type to itself (#295).
        case (expNullable: NullableType, actNullable: NullableType) =>
          isAssignableWithBoxing(expNullable.innerType, actNullable.innerType, table)
        // `T? ← T`: a non-null value widens to the nullable parameter.
        case (expNullable: NullableType, _) =>
          isAssignableWithBoxing(expNullable.innerType, actual, table)
        case (expectedApplied: AppliedClassType, actualApplied: AppliedClassType) =>
          // Type arguments are INVARIANT (generics are erasure-based, no
          // declaration-site variance): Box[Dog] is NOT a Box[Animal], since a
          // mutable Box[Animal] could then store a Cat and corrupt the Box[Dog].
          // Require each argument to be mutually assignable rather than merely a
          // subtype; wildcard variance is still handled by isSuperType above.
          sameClass(expectedApplied.raw, actualApplied.raw) &&
            expectedApplied.typeArguments.length == actualApplied.typeArguments.length &&
            expectedApplied.typeArguments.zip(actualApplied.typeArguments).forall { (expArg, actArg) =>
              isAssignableWithBoxing(expArg, actArg, table) && isAssignableWithBoxing(actArg, expArg, table)
            }
        case _ =>
          false
}
