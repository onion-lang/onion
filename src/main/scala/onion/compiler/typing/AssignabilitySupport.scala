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

  /**
   * Rebuilds an empty collection literal with the element type(s) of the
   * expected collection, so it target-types like `val xs: List[Int] = []`. The
   * literal type is interned, so when it already matches, the same term is
   * returned (terminating the retype in processAssignable).
   */
  private def retypeEmptyCollectionLiteral(expected: Type, actual: Term): Term = {
    val listRaw = bodyContext.load("java.util.List")
    val mapRaw = bodyContext.load("java.util.Map")
    actual match {
      case ll: ListLiteral if ll.elements.isEmpty =>
        expected match {
          case e: AppliedClassType if e.typeArguments.length == 1 && TypeRules.isSuperType(e.raw, listRaw) =>
            val nt = AppliedClassType(listRaw, e.typeArguments.toList)
            if (nt eq actual.`type`) actual else new ListLiteral(ll.elements, nt)
          case _ => actual
        }
      case ml: MapLiteral if ml.keys.isEmpty =>
        expected match {
          case e: AppliedClassType if e.typeArguments.length == 2 && TypeRules.isSuperType(e.raw, mapRaw) =>
            val nt = AppliedClassType(mapRaw, e.typeArguments.toList)
            if (nt eq actual.`type`) actual else new MapLiteral(ml.keys, ml.values, nt)
          case _ => actual
        }
      case _ => actual
    }
  }

  /** The compile-time int value of an integer literal or its unary negation,
    * for constant narrowing (`100`, `-128`). */
  private def constantIntOf(term: Term): Option[Int] = term match {
    case iv: IntValue => Some(iv.value)
    case u: UnaryTerm if u.kind == UnaryTerm.Kind.MINUS =>
      u.operand match { case iv: IntValue => Some(-iv.value); case _ => None }
    case _ => None
  }

  /** Whether an integer literal fits the range of a narrow integral target,
    * enabling `val b: Byte = 100` style constant narrowing. */
  private def integerLiteralFits(bt: BasicType, value: Int): Boolean = bt match {
    case BasicType.BYTE  => value >= -128 && value <= 127
    case BasicType.SHORT => value >= -32768 && value <= 32767
    case BasicType.CHAR  => value >= 0 && value <= 65535
    case _ => false
  }

  def processAssignable(node: AST.Node, expected: Type, actual: Term): Term = {
    if (actual == null) return null
    // Target-type an empty collection literal ([] / [:]) to the expected
    // collection type, then proceed normally (so `foo([])` binds [] to the
    // parameter's element type instead of the default Object).
    val retyped = retypeEmptyCollectionLiteral(expected, actual)
    if (retyped ne actual) return processAssignable(node, expected, retyped)
    if (actual.`type`.isBottomType) return actual
    if (expected == actual.`type`) return actual

    // Constant narrowing: an integer literal (or its negation) that fits the
    // target range target-types to Byte/Short/Char (like Java's `byte b = 100`).
    expected match {
      case bt: BasicType if constantIntOf(actual).exists(v => integerLiteralFits(bt, v)) =>
        return new AsInstanceOf(node.location, actual, bt)
      case _ =>
    }

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

    if (actual.`type`.isNullType && !expected.isBasicType && !expected.isNullable && !expected.isNullType) {
      // The null literal flowing into a non-nullable type is almost always a
      // bug waiting for an NPE; values coming from Java stay unchecked
      // (Kotlin's platform-type dilemma — see issue #132)
      bodyContext.warningReporter.nullToNonNullable(node.location, expected.displayName)
    }

    if (expected.isBasicType && actual.`type`.isNullType) {
      // null can never be assigned to a primitive; unboxing it would crash
      bodyContext.report(INCOMPATIBLE_TYPE, node, expected, actual.`type`)
      return null
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
        def argsMatch(view: TypedAST.AppliedClassType): Boolean =
          ae.typeArguments.length == view.typeArguments.length &&
            ae.typeArguments.zip(view.typeArguments).forall { case (e, a) => structurallyAssignable(e, a) }
        if (TypeRelations.sameClass(ae.raw, aa.raw)) argsMatch(aa)
        else
          // Different raw classes: find aa's applied view of ae.raw in its
          // hierarchy (ArrayList[T] exposes List[T]) and compare the type
          // arguments structurally, so a generic subtype is assignable to its
          // generic supertype when the arguments match (#269). Without this, a
          // type-variable-parameterized subtype (matched via this path because
          // the expected type contains a type variable) was rejected even though
          // the concrete-argument form is accepted by the normal hierarchy check.
          AppliedTypeViews.collectAppliedViewsFrom(aa)
            .collectFirst { case (raw, view) if TypeRelations.sameClass(raw, ae.raw) && argsMatch(view) => true }
            .getOrElse(false)
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
