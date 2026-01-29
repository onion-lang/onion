package onion.compiler.typing

import onion.compiler.AST
import onion.compiler.LocalContext
import onion.compiler.TypedAST.{NullableType, Type}

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
   */
  case class NarrowingInfo(
    positive: Map[String, Type],
    negative: Map[String, Type]
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
    typeResolver: AST.TypeNode => Type
  ): NarrowingInfo = {
    condition match {
      // x is SomeType -> narrow x to SomeType in then-branch
      case AST.IsInstance(_, AST.Id(_, name), typeRef) =>
        val binding = context.lookup(name)
        if (binding != null && !binding.isMutable) {
          val targetType = typeResolver(typeRef)
          if (targetType != null) {
            return NarrowingInfo(Map(name -> targetType), Map.empty)
          }
        }
        NarrowingInfo.empty

      // x != null -> narrow x from T? to T in then-branch
      case AST.NotEqual(_, AST.Id(_, name), AST.NullLiteral(_)) =>
        extractNullCheckNarrowing(name, context, positive = true)
      case AST.NotEqual(_, AST.NullLiteral(_), AST.Id(_, name)) =>
        extractNullCheckNarrowing(name, context, positive = true)

      // x == null -> narrow x from T? to T in else-branch
      case AST.Equal(_, AST.Id(_, name), AST.NullLiteral(_)) =>
        extractNullCheckNarrowing(name, context, positive = false)
      case AST.Equal(_, AST.NullLiteral(_), AST.Id(_, name)) =>
        extractNullCheckNarrowing(name, context, positive = false)

      // cond1 && cond2 -> both narrowings apply in then-branch
      case AST.LogicalAnd(_, left, right) =>
        val leftNarrowing = extractNarrowing(left, context, typeResolver)
        val rightNarrowing = extractNarrowing(right, context, typeResolver)
        NarrowingInfo(leftNarrowing.positive ++ rightNarrowing.positive, Map.empty)

      case _ => NarrowingInfo.empty
    }
  }

  /**
   * Helper for null-check narrowing extraction.
   */
  private def extractNullCheckNarrowing(name: String, context: LocalContext, positive: Boolean): NarrowingInfo = {
    val binding = context.lookup(name)
    if (binding != null && !binding.isMutable) {
      binding.tp match {
        case nullableType: NullableType =>
          if (positive) {
            NarrowingInfo(Map(name -> nullableType.innerType), Map.empty)
          } else {
            NarrowingInfo(Map.empty, Map(name -> nullableType.innerType))
          }
        case _ => NarrowingInfo.empty
      }
    } else {
      NarrowingInfo.empty
    }
  }
}
