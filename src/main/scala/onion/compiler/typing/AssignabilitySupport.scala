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

  def processAssignable(node: AST.Node, expected: Type, actual: Term): Term = {
    if (actual == null) return null
    // Target-type an empty collection literal ([] / [:]) to the expected
    // collection type, then proceed normally (so `foo([])` binds [] to the
    // parameter's element type instead of the default Object).
    val retyped = retypeEmptyCollectionLiteral(expected, actual)
    if (retyped ne actual) return processAssignable(node, expected, retyped)
    if (actual.`type`.isBottomType) {
      // A StatementTerm transfers control (return/throw/break/continue) on every
      // path and leaves nothing on the operand stack; unboxing it would generate
      // dead code whose checkcast pops an empty stack (issue #returnInSelect).
      // Return the term unchanged so emitReturn can recognise it and skip the
      // redundant gen.returnValue().
      if (actual.isInstanceOf[StatementTerm]) return actual
      // A bottom-typed value never actually materializes (the expression throws
      // or otherwise never completes), but the bytecode path still has to
      // verify. When it came from an erased generic call -- `f.await()` on a
      // `Future[Nothing]` -- the JVM sees a reference on the stack, so storing
      // it into a primitive slot needs the same unboxing any reference would
      // (issue #314). Without this, codegen produced a frame the verifier
      // rejected.
      expected match {
        case bt: BasicType if bt != BasicType.VOID && !actual.isBasicType =>
          val boxedType = Boxing.boxedType(bodyContext.table, bt)
          return Boxing.unboxing(bodyContext.table, new AsInstanceOf(node.location, actual, boxedType), bt)
        case _ => return actual
      }
    }
    if (expected == actual.`type`) return actual

    // Constant narrowing: an integer literal (or its negation) that fits the
    // target range target-types to Byte/Short/Char (like Java's `byte b = 100`).
    expected match {
      case bt: BasicType if ConstantNarrowing.constantIntOf(actual).exists(v => ConstantNarrowing.fits(bt, v)) =>
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
        // The source is a boxed reference (typically a Java-interop platform
        // value, e.g. Json::getInt's Integer) with no Onion-tracked
        // nullability; unboxing it to a non-null primitive crashes with a raw
        // NPE if the value is actually null (#318). Mirrors the W0012
        // null-to-non-nullable trade-off above: warn rather than block.
        bodyContext.warningReporter.platformUnboxing(node.location, actual.`type`.displayName, targetBasicType.displayName)
        return Boxing.unboxing(bodyContext.table, actual, targetBasicType)
      }
    }

    def containsTypeVariable(typeToCheck: Type): Boolean =
      TypeCheckingHelpers.containsTypeVariable(typeToCheck)

    def structurallyAssignable(expected: Type, actual: Type): Boolean = (expected, actual) match {
      case (_, nt) if nt.isNullType =>
        // The null literal is assignable to any reference (non-basic) target,
        // including a generic type parameterized by a type variable (Node[T]).
        // The nullability concern is already handled by the W0012
        // nullToNonNullable warning emitted above, so this must not become a
        // hard INCOMPATIBLE_TYPE error — matching the non-type-variable path
        // (TypeRelations.isAssignableWithBoxing) which accepts null here (#283).
        !expected.isBasicType
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
        // Boxing-aware terminal comparison: erased generic slots hold boxed
        // types, so a closure whose result is a primitive `int` still fills a
        // `Function1[T, Integer]`'s return position (issue #319). Plain
        // isAssignable is not boxing-aware and false-negatives on Integer vs int,
        // yielding the self-contradictory "Function1[T,Int] expected, ... used"
        // when `map` is applied to a raw List. isAssignableWithBoxing keeps the
        // same acceptance for reference types while adding primitive<->boxed.
        TypeRelations.isAssignableWithBoxing(expected, actual, bodyContext.table)
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
