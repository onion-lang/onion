package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class AssignabilitySupport(
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

  def processAssignable(node: AST.Node, expected: Type, actual: Term, suppressNullWarning: Boolean = false): Term = {
    if (actual == null) return null
    // Target-type an empty collection literal ([] / [:]) to the expected
    // collection type, then proceed normally (so `foo([])` binds [] to the
    // parameter's element type instead of the default Object).
    val retyped = retypeEmptyCollectionLiteral(expected, actual)
    if (retyped ne actual) return processAssignable(node, expected, retyped, suppressNullWarning)
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
      // (Kotlin's platform-type dilemma — see issue #132). suppressNullWarning
      // is set by a call-argument path whose callee is Java-declared (#2116):
      // the parameter's own nullness is unknown, and it is not a signature the
      // caller can edit. setSourceFile here (not only from TypingDiagnostics)
      // because this can be the very first warning of the unit, before anything
      // else has set it (#2238).
      if (!suppressNullWarning) {
        bodyContext.warningReporter.setSourceFile(bodyContext.sourceFile)
        bodyContext.warningReporter.nullToNonNullable(node.location, expected.displayName)
      }
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
        // An elvis already handled null: with a non-null fallback its (boxed)
        // result can never be null, so there is nothing to warn about.
        val isElvis = actual match {
          case b: BinaryTerm => b.kind == BinaryTerm.Kind.ELVIS
          case _ => false
        }
        // A direct call through a FunctionN value (`f(x)`, desugared to
        // `f.call(x)`) whose declared return type argument is a concrete,
        // non-nullable type (not `T?`) can never actually produce null:
        // every implementation autoboxes exactly that declared type on
        // return. Unlike a true Java generic method (Colls::fold, whose type
        // variable R is unified per call site and could be bound to
        // anything, including a genuinely nullable type), a FunctionN's
        // return type argument is fixed by the function's own Onion type, so
        // the compiler already knows the result can't be null here.
        val isNonNullFunctionCall = actual match {
          case cast: AsInstanceOf =>
            cast.target match {
              case call: Call if call.method.name == "call" =>
                call.target.`type` match {
                  case applied: AppliedClassType if applied.raw.name.startsWith("onion.Function") =>
                    !applied.typeArguments.last.isNullable
                  case _ => false
                }
              case _ => false
            }
          case _ => false
        }
        if (!isElvis && !isNonNullFunctionCall) {
          // Same reason as the nullToNonNullable call above (#2238): this can
          // be the first warning of the unit.
          bodyContext.warningReporter.setSourceFile(bodyContext.sourceFile)
          bodyContext.warningReporter.platformUnboxing(node.location, actual.`type`.displayName, targetBasicType.displayName)
        }
        return Boxing.unboxing(bodyContext.table, actual, targetBasicType)
      }
    }

    def containsTypeVariable(typeToCheck: Type): Boolean =
      TypeCheckingHelpers.containsTypeVariable(typeToCheck)

    // An F-bound names the variable through the placeholder frozen while the
    // bounds were resolved, so point that self-reference back at `tv` first.
    def appliedSelfBound(tv: TypedAST.TypeVariableType): Option[TypedAST.AppliedClassType] =
      TypeSubstitution.substituteType(tv.upperBound, Map(tv.name -> tv), Map.empty, defaultToBound = false) match {
        case bound: TypedAST.AppliedClassType => Some(bound)
        case _ => None
      }

    // `nested` is true inside a type-argument comparison, where type arguments are
    // invariant and a variable must not widen to its bound; `strict` makes two type
    // variables match only by identity (used when comparing a variable's own bound).
    def structurallyAssignable(expected: Type, actual: Type, nested: Boolean = false, strict: Boolean = false): Boolean = (expected, actual) match {
      case (_, nt) if nt.isNullType =>
        // The null literal is assignable to any reference (non-basic) target,
        // including a generic type parameterized by a type variable (Node[T]).
        // The nullability concern is already handled by the W0012
        // nullToNonNullable warning emitted above, so this must not become a
        // hard INCOMPATIBLE_TYPE error — matching the non-type-variable path
        // (TypeRelations.isAssignableWithBoxing) which accepts null here (#283).
        !expected.isBasicType
      case (tv: TypedAST.TypeVariableType, _) =>
        // Pass `tv` itself, not its erased `upperBound`: when `actual` is
        // itself a type variable (e.g. `T` in `[B, T extends B]`), only
        // `isSuperType`'s own identity-aware chain walk can tell "T's
        // bound chain passes through `tv`" apart from "both happen to
        // erase to the same root class" (#2259's unsoundness) -- erasing
        // `tv` here first would throw away exactly the identity it needs.
        TypeRules.isSuperType(tv, actual)
      case (ae: TypedAST.AppliedClassType, aa: TypedAST.AppliedClassType) =>
        def argsMatch(view: TypedAST.AppliedClassType): Boolean =
          ae.typeArguments.length == view.typeArguments.length &&
            ae.typeArguments.zip(view.typeArguments).forall { case (e, a) =>
              // Type arguments are invariant: List[T] is not a List[B] even
              // when T extends B (a mutable List[B] view could then store
              // something that only fits B, corrupting the underlying
              // List[T]). Checking only one direction let a type-variable
              // argument through covariantly via isSuperType's chain walk
              // above (#2259 review repro: returning a `List[T]` field as
              // `List[B]` compiled, then a `List[B].add` of a non-T value
              // threw a ClassCastException on the real `List[T]`).
              // Scoped to a plain type variable on either side -- a
              // wildcard (`Function1[T, ?]`) already has its own,
              // deliberately one-directional covariant/contravariant
              // rules (handled elsewhere), and requiring both directions
              // for it rejected ordinary covariant wildcard uses that
              // never involved a chained bound at all.
              val involvesTypeVariable = e.isInstanceOf[TypedAST.TypeVariableType] || a.isInstanceOf[TypedAST.TypeVariableType]
              e match {
                // `Collection[? extends T]` (the JDK's `addAll`) takes a
                // `List[T]`: the wildcard's own bound decides, one way
                // (#2341). `? super L` is the mirror image.
                case w: TypedAST.WildcardType if !a.isInstanceOf[TypedAST.WildcardType] =>
                  // A bound that is itself a type variable is abstract: only
                  // that variable (or one whose bound chain reaches it)
                  // fits. A concrete type, `Object` or an unrelated `U`
                  // must not pass just because it erases to the same
                  // class, or `xs.addAll(o)` with `o: List[Object]`
                  // would pollute a `List[T]`.
                  w.lowerBound match {
                    case Some(lb: TypedAST.TypeVariableType) =>
                      a match {
                        case av: TypedAST.TypeVariableType => TypeRules.isSuperTypeByIdentity(av, lb)
                        case _ => structurallyAssignable(a, lb)
                      }
                    case Some(lb) => structurallyAssignable(a, lb)
                    case None =>
                      w.upperBound match {
                        case tv: TypedAST.TypeVariableType => TypeRules.isSuperTypeByIdentity(tv, a)
                        case ub => structurallyAssignable(ub, a)
                      }
                  }
                case ev: TypedAST.TypeVariableType if strict && a.isInstanceOf[TypedAST.TypeVariableType] =>
                  ev.widen eq a.asInstanceOf[TypedAST.TypeVariableType].widen
                case _ if involvesTypeVariable => structurallyAssignable(e, a, nested = true, strict = strict) && structurallyAssignable(a, e, nested = true, strict = strict)
                case _ => structurallyAssignable(e, a, nested = true, strict = strict)
              }
            }
        if (TypeRelations.sameClass(ae.raw, aa.raw)) argsMatch(aa)
        else
          // Different raw classes: find aa's applied view of ae.raw in its
          // hierarchy (ArrayList[T] exposes List[T]) and compare the type
          // arguments structurally, so a generic subtype is assignable to its
          // generic supertype when the arguments match (#269). Without this, a
          // type-variable-parameterized subtype (matched via this path because
          // the expected type contains a type variable) was rejected even though
          // the concrete-argument form is accepted by the normal hierarchy check.
          bodyContext.table.appliedViewsOf(aa)(AppliedTypeViews.collectAppliedViewsFrom(aa))
            .collectFirst { case (raw, view) if TypeRelations.sameClass(raw, ae.raw) && argsMatch(view) => true }
            .getOrElse(false)
      case (ae: TypedAST.AppliedClassType, tv: TypedAST.TypeVariableType)
          if !nested && appliedSelfBound(tv).isDefined =>
        // A type variable fits an applied type its own bound reaches
        // (`T extends Comparable[T]` is a `Comparable[T]`). A raw bound
        // (`T extends Comparable`) says nothing about type arguments, so it
        // keeps the cases below, which reject it.
        structurallyAssignable(ae, appliedSelfBound(tv).get, nested = true, strict = true)
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
      actual.`type` match {
        // A nullable value (`g: Foo?`, never null-checked) that failed the
        // compatibility check above precisely because it's nullable -- the
        // check already accepts it wherever the language means to (widening
        // to `Object`, a bare/platform type parameter's upper bound, ...) --
        // gets the null-safety diagnostic (E0070) that every other
        // dereference-like use of a nullable value already reports, instead
        // of the generic INCOMPATIBLE_TYPE (E0000) below, which hides that
        // the only problem is a missing null check. Covers a `val`/`var`
        // declaration's initializer, a `return` value, a plain reassignment,
        // and a field initializer, all of which share this helper -- mirrors
        // the sibling receiver-nullability checks (MethodTargetTypingSupport.
        // normalizeMethodCallTarget, MemberSelectionResolutionSupport.
        // normalizeMemberSelectionTarget), which already special-case
        // NullableType up front for their own (dereference-target) cases.
        // Only when the value without its `?` would fit; otherwise the
        // mismatch is elsewhere (a type argument, say) and `!!`/`?:` do not help.
        case nullable: NullableType
            if (if (containsTypeVariable(expected)) structurallyAssignable(expected, nullable.innerType)
                else TypeRelations.isAssignableWithBoxing(expected, nullable.innerType, bodyContext.table)) =>
          bodyContext.report(NULLABLE_MEMBER_ACCESS, node, nullable.displayName, "assignment")
        case _ if expected == BasicType.BYTE && ConstantNarrowing.isHexEndingInB(actual) =>
          bodyContext.report(INCOMPATIBLE_TYPE, node, expected, actual.`type`, SemanticErrorReporter.HexDigitBHint)
        case _ =>
          bodyContext.report(INCOMPATIBLE_TYPE, node, expected, actual.`type`)
      }
      return null
    }
    new AsInstanceOf(node.location, actual, expected)
  }
}
