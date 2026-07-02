package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

final class ExpressionFormTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {
  private val constructionTyping = new ConstructionTyping(typing, bodyContext, body)
  private val stringInterpolationTyping = new StringInterpolationTyping(typing, bodyContext, body)

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    constructionTyping.typeIndexing(node, context)

  def typeSafeIndexing(node: AST.SafeIndexing, context: LocalContext): Option[Term] =
    constructionTyping.typeSafeIndexing(node, context)

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] =
    constructionTyping.typeNewArray(node, context)

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] =
    constructionTyping.typeNewArrayWithValues(node, context)

  def typeNewObject(node: AST.NewObject, context: LocalContext, expected: Type = null): Option[Term] =
    constructionTyping.typeNewObject(node, context, expected)

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] =
    stringInterpolationTyping.typeStringInterpolation(node, context)

  def typeElvis(node: AST.Elvis, context: LocalContext): Option[Term] =
    for {
      left <- typed(node.lhs, context)
      right0 <- typed(node.rhs, context)
      result <- {
        val leftT = left.`type`
        // A nullable primitive (Int?) stores a primitive inner in the type but a
        // boxed value at runtime (asmType(Int?) == Integer), so its non-null form
        // is the boxed type.
        def boxedInner(inner: Type): Type = inner match {
          case bt: BasicType if bt != BasicType.VOID => Boxing.boxedType(bodyContext.table, bt)
          case other => other
        }
        // The non-null value type carried by the left operand.
        val leftNonNull: Type = leftT match {
          case n: NullableType => boxedInner(n.innerType)
          case tv: TypeVariableType if tv.nullability == Nullability.Nullable => tv.nonNullView
          case t => t
        }
        // The elvis is emitted as `dup; ifnonnull; pop; <rhs>`: the left must be
        // a reference and both branches must leave the same stack type. A
        // primitive fallback against a boxed result (e.g. `Int? ?: -1`, where the
        // left is a boxed Integer) is boxed so both branches agree; the boxed
        // result unboxes at the use site as usual.
        val right: Term =
          if (right0.isBasicType && !leftNonNull.isBasicType) Boxing.boxing(bodyContext.table, right0)
          else right0
        // Compare against the right operand's non-null core: a nullable fallback
        // (`a ?: b` where both are T?) is valid and yields a nullable result
        // (computed below), which also makes chained `a ?: b ?: c` type-check.
        val rightNonNull: Type = right.`type` match {
          case n: NullableType => boxedInner(n.innerType)
          case tv: TypeVariableType if tv.nullability == Nullability.Nullable => tv.nonNullView
          case other => other
        }
        if (leftT.isBasicType || (!leftNonNull.isBottomType && !TypeRules.isAssignable(leftNonNull, rightNonNull))) {
          bodyContext.report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftT, right0.`type`))
          None
        } else {
          // When the fallback can't be null, the whole expression can't be
          // null either: T? ?: T yields T, and a nullable type variable
          // yields its non-null view
          def definitelyNonNull(t: Type): Boolean = t match {
            case _: NullableType => false
            case t if t.isNullType => false
            case tv: TypeVariableType => tv.nullability == Nullability.NonNull
            case _ => true
          }
          val resultType = leftT match {
            case n: NullableType if definitelyNonNull(right.`type`) => boxedInner(n.innerType)
            case tv: TypeVariableType if tv.nullability == Nullability.Nullable && definitelyNonNull(right.`type`) =>
              tv.nonNullView
            case t => t
          }
          Some(new BinaryTerm(ELVIS, resultType, left, right))
        }
      }
    } yield result

  /**
   * Non-null assertion expr!!: strips one level of nullability, trading a
   * possible NullPointerException at the assertion site. Primitives can't
   * be null, so the assertion is a no-op for them.
   */
  def typeNotNullAssertion(node: AST.NotNullAssertion, context: LocalContext): Option[Term] =
    typed(node.term, context).map { target =>
      target.`type` match {
        case n: NullableType => new NonNullAssert(target, n.innerType)
        case tv: TypeVariableType if tv.nullability == Nullability.Nullable =>
          new NonNullAssert(target, tv.nonNullView)
        case bt if bt.isBasicType => target
        case t if t.isNullType =>
          // null!! always throws; type as Object so the expression stays usable
          new NonNullAssert(target, bodyContext.rootClass)
        case other =>
          // Platform / non-null reference values still get the runtime check
          new NonNullAssert(target, other)
      }
    }

  def typeCast(node: AST.Cast, context: LocalContext): Option[Term] = node.src match {
    // A lambda takes its type from its target, so `(() -> ...) as Runnable` must
    // type the lambda against the cast destination -- enabling SAM conversion to
    // a functional interface (a bare lambda would default to onion.FunctionN).
    case _: AST.ClosureExpression =>
      typing.mapFrom(node.to, bodyContext.mapper).flatMap { destination =>
        typed(node.src, context, destination).flatMap { term =>
          if (!isValidCast(term.`type`, destination)) {
            bodyContext.report(INCOMPATIBLE_TYPE, node, destination, term.`type`)
            None
          } else {
            Some(new AsInstanceOf(term, destination))
          }
        }
      }
    case _ =>
      typed(node.src, context).flatMap { term =>
        typing.mapFrom(node.to, bodyContext.mapper).flatMap { destination =>
          if (!isValidCast(term.`type`, destination)) {
            bodyContext.report(INCOMPATIBLE_TYPE, node, destination, term.`type`)
            None
          } else {
            Some(new AsInstanceOf(term, destination))
          }
        }
      }
  }

  /**
   * Determine whether a cast from `source` to `destination` is statically valid.
   * Allows reference-to-reference casts along the type hierarchy, boxing/unboxing
   * between a primitive and its single boxed counterpart, and casts from Object
   * (which may fail at runtime). Rejects unrelated casts such as String -> Int.
   */
  private def isValidCast(source: Type, destination: Type): Boolean = {
    if (source eq destination) return true
    (source, destination) match {
      case (_: NullType, bt: BasicType) =>
        false
      case (bt: BasicType, ct: ClassType) =>
        (Boxing.boxedType(bodyContext.table, bt) eq ct) || ct.isInstanceOf[TypeVariableType]
      case (ct: ClassType, bt: BasicType) =>
        (Boxing.boxedType(bodyContext.table, bt) eq ct) || (ct eq bodyContext.rootClass) || ct.isInstanceOf[TypeVariableType]
      case (ct: ClassType, dt: ClassType) =>
        TypeRules.isSuperType(dt, ct) || TypeRules.isSuperType(ct, dt) || ct.isInstanceOf[TypeVariableType] || dt.isInstanceOf[TypeVariableType]
      case (bt1: BasicType, bt2: BasicType) =>
        (bt1 eq bt2) || (isNumericBasic(bt1) && isNumericBasic(bt2))
      case _ => true
    }
  }

  private def isNumericBasic(bt: BasicType): Boolean = bt match {
    case BasicType.INT | BasicType.LONG | BasicType.SHORT | BasicType.BYTE |
         BasicType.FLOAT | BasicType.DOUBLE | BasicType.CHAR => true
    case _ => false
  }

  def typeIsInstance(node: AST.IsInstance, context: LocalContext): Option[Term] =
    for {
      target <- typed(node.target, context)
      destinationType <- typing.mapFrom(node.typeRef, bodyContext.mapper)
    } yield new InstanceOf(target, destinationType)

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)
}
