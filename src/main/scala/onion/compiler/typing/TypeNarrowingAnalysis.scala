package onion.compiler.typing

import onion.compiler.AST
import onion.compiler.LocalContext
import onion.compiler.TypedAST.{Nullability, NullableType, Type, TypeVariableType}

/**
 * Type narrowing analysis for smart casts in if-expressions.
 *
 * Analyzes condition expressions to extract type narrowing information
 * that can be used to narrow variable types in then/else branches.
 *
 * Examples:
 * - `x is SomeType` -> narrow x to SomeType in then-branch
 * - `x != null` -> narrow x from T? to T in then-branch
 * - `x == null` -> narrow x from T? to T in else-branch
 */
private[typing] object TypeNarrowingAnalysis {

  /**
   * Type narrowing information extracted from a condition.
   *
   * @param positive Narrowings to apply in the then-branch (condition is true)
   * @param negative Narrowings to apply in the else-branch (condition is false)
   * @param mutablePositive Like `positive` but for reassignable (`var`) locals
   *                        (issues #288/#289). These are only safe to apply if
   *                        the var is not reassigned in the condition or within
   *                        the guarded region, so the caller must gate them
   *                        (they are applied as flow-sensitive narrowings).
   * @param mutableNegative Like `negative` but for reassignable (`var`) locals.
   */
  case class NarrowingInfo(
    positive: Map[String, Type],
    negative: Map[String, Type],
    mutablePositive: Map[String, Type] = Map.empty,
    mutableNegative: Map[String, Type] = Map.empty
  )

  object NarrowingInfo {
    val empty: NarrowingInfo = NarrowingInfo(Map.empty, Map.empty)
  }

  /**
   * Extracts type narrowing information from a condition expression.
   *
   * @param condition The condition expression to analyze
   * @param context The local context for variable lookup
   * @param typeResolver Function to resolve AST type nodes to types
   * @return NarrowingInfo with positive and negative narrowings
   */
  def extractNarrowing(
    condition: AST.Expression,
    context: LocalContext,
    typeResolver: AST.TypeNode => Option[Type],
    fieldNarrow: String => Option[Type] = _ => None
  ): NarrowingInfo = {
    condition match {
      // x is SomeType -> narrow x to SomeType in then-branch
      case AST.IsInstance(_, AST.Id(_, name), typeRef) =>
        val binding = context.lookup(name)
        if (binding != null && !binding.isMutable) {
          typeResolver(typeRef) match {
            case Some(targetType) =>
              return NarrowingInfo(Map(name -> targetType), Map.empty)
            case None => ()
          }
        }
        NarrowingInfo.empty

      // x != null -> narrow x from T? to T in then-branch
      case AST.NotEqual(_, NullCheckTarget(name, isField), AST.NullLiteral(_)) =>
        extractNullCheckNarrowing(name, context, positive = true, fieldNarrow, isField)
      case AST.NotEqual(_, AST.NullLiteral(_), NullCheckTarget(name, isField)) =>
        extractNullCheckNarrowing(name, context, positive = true, fieldNarrow, isField)

      // x == null -> narrow x from T? to T in else-branch
      case AST.Equal(_, NullCheckTarget(name, isField), AST.NullLiteral(_)) =>
        extractNullCheckNarrowing(name, context, positive = false, fieldNarrow, isField)
      case AST.Equal(_, AST.NullLiteral(_), NullCheckTarget(name, isField)) =>
        extractNullCheckNarrowing(name, context, positive = false, fieldNarrow, isField)

      // cond1 && cond2 -> both narrowings apply in then-branch
      case AST.LogicalAnd(_, left, right) =>
        val leftNarrowing = extractNarrowing(left, context, typeResolver, fieldNarrow)
        val rightNarrowing = extractNarrowing(right, context, typeResolver, fieldNarrow)
        NarrowingInfo(
          leftNarrowing.positive ++ rightNarrowing.positive, Map.empty,
          leftNarrowing.mutablePositive ++ rightNarrowing.mutablePositive, Map.empty
        )

      // cond1 || cond2 -> De Morgan dual of &&: the whole expression being FALSE
      // implies !cond1 && !cond2, so both else-branch (negative) narrowings apply
      // in the else/fall-through. Nothing is soundly known when it is TRUE (only
      // one operand need hold), so the positive side stays empty. This makes
      // `if x == null || x.isEmpty() { return } ; x.foo()` narrow x on the
      // fall-through (#302), mirroring the && then-branch narrowing (#294).
      case AST.LogicalOr(_, left, right) =>
        val leftNarrowing = extractNarrowing(left, context, typeResolver, fieldNarrow)
        val rightNarrowing = extractNarrowing(right, context, typeResolver, fieldNarrow)
        NarrowingInfo(
          Map.empty, leftNarrowing.negative ++ rightNarrowing.negative,
          Map.empty, leftNarrowing.mutableNegative ++ rightNarrowing.mutableNegative
        )

      // !cond -> swap polarity: if !(o is String) narrows o in the else branch
      case AST.Not(_, inner) =>
        val innerNarrowing = extractNarrowing(inner, context, typeResolver, fieldNarrow)
        NarrowingInfo(
          innerNarrowing.negative, innerNarrowing.positive,
          innerNarrowing.mutableNegative, innerNarrowing.mutablePositive
        )

      case _ => NarrowingInfo.empty
    }
  }

  /**
   * Matches the operand of a null-check that this analysis can narrow: either a
   * bare identifier `x` or an explicit current-instance field access
   * `this.field` / `self.field`. Returns the target name and whether the form
   * was an explicit `this`-field selection (in which case only a field, never a
   * local of the same name, is eligible for narrowing).
   */
  private object NullCheckTarget {
    def unapply(expr: AST.Expression): Option[(String, Boolean)] = expr match {
      case AST.Id(_, name) => Some((name, false))
      case AST.MemberSelection(_, AST.CurrentInstance(_), name) => Some((name, true))
      case _ => None
    }
  }

  /**
   * Helper for null-check narrowing extraction.
   *
   * @param fieldOnly when true the operand was written as `this.field`, so a
   *                  same-named local must NOT be considered -- only a `val`
   *                  field of the current class.
   */
  private def extractNullCheckNarrowing(
    name: String, context: LocalContext, positive: Boolean, fieldNarrow: String => Option[Type],
    fieldOnly: Boolean = false
  ): NarrowingInfo = {
    val binding = if (fieldOnly) null else context.lookup(name)
    // The non-null view of a nullable local/field type, or None if not nullable.
    def nonNull(tp: Type): Option[Type] = tp match {
      case nullableType: NullableType => Some(nullableType.innerType)
      // A nullable type variable narrows to its non-null view.
      case tv: TypeVariableType if tv.nullability == Nullability.Nullable => Some(tv.nonNullView)
      case _ => None
    }
    if (binding != null && binding.isMutable) {
      // A reassignable `var`: not eligible for whole-scope smart cast, but it can
      // be narrowed flow-sensitively (issues #288/#289) if the caller verifies it
      // is not reassigned between the check and the guarded region. Surface it in
      // the mutable* maps so the caller can gate it.
      nonNull(binding.tp) match {
        case Some(t) =>
          if (positive) NarrowingInfo(Map.empty, Map.empty, Map(name -> t), Map.empty)
          else NarrowingInfo(Map.empty, Map.empty, Map.empty, Map(name -> t))
        case None => NarrowingInfo.empty
      }
    } else {
      val narrowed: Option[Type] =
        if (binding != null) nonNull(binding.tp)
        else {
          // No local of this name: it may be an implicitly-accessed `val` field,
          // which is immutable and so safe to narrow.
          fieldNarrow(name)
        }
      narrowed match {
        case Some(t) => if (positive) NarrowingInfo(Map(name -> t), Map.empty) else NarrowingInfo(Map.empty, Map(name -> t))
        case None => NarrowingInfo.empty
      }
    }
  }
}
