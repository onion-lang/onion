package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind as BinaryKind
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class SimpleExpressionTypingSupport(
  bodyContext: TypingBodyContext,
  typed: (AST.Expression, LocalContext, Type) => Option[Term],
  typeMemberSelection: (AST.MemberSelection, LocalContext) => Option[Term],
  typeAssignment: (AST.Assignment, LocalContext) => Option[Term]
) {
  def typeSimple(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    node match {
      case node@AST.AdditionAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, ADD, context)
      case node@AST.SubtractionAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, SUBTRACT, context)
      case node@AST.MultiplicationAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, MULTIPLY, context)
      case node@AST.DivisionAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, DIVIDE, context)
      case node@AST.ModuloAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, MOD, context)
      case node@AST.BitAndAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, BIT_AND, context)
      case node@AST.BitOrAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, BIT_OR, context)
      case node@AST.XorAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, XOR, context)
      case node@AST.LeftShiftAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, BIT_SHIFT_L2, context)
      case node@AST.MathRightShiftAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, BIT_SHIFT_R2, context)
      case node@AST.LogicalRightShiftAssignment(_, left, right) =>
        typeBinaryAssignment(node, left, right, BIT_SHIFT_R3, context)
      case node@AST.CharacterLiteral(loc, v) =>
        Some(new CharacterValue(loc, v))
      case node@AST.ByteLiteral(loc, v) =>
        Some(new ByteValue(loc, v))
      case node@AST.ShortLiteral(loc, v) =>
        Some(new ShortValue(loc, v))
      case node@AST.IntegerLiteral(loc, v) =>
        Some(new IntValue(loc, v))
      case node@AST.LongLiteral(loc, v) =>
        Some(new LongValue(loc, v))
      case node@AST.FloatLiteral(loc, v) =>
        Some(new FloatValue(loc, v))
      case node@AST.DoubleLiteral(loc, v) =>
        Some(new DoubleValue(loc, v))
      case node@AST.BooleanLiteral(loc, v) =>
        Some(new BoolValue(loc, v))
      case node: AST.ListLiteral =>
        typeListLiteral(node, context, expected)
      case node: AST.MapLiteral =>
        typeMapLiteral(node, context, expected)
      case node@AST.NullLiteral(loc) =>
        Some(new NullValue(loc))
      case node@AST.CurrentInstance(loc) =>
        typeCurrentInstance(node, context)
      case node@AST.Id(loc, name) =>
        typeIdentifier(node, context)
      case node: AST.UnqualifiedFieldReference =>
        typeUnqualifiedFieldReference(node, context)
      case node@AST.StringLiteral(loc, value) =>
        Some(new StringValue(loc, value, bodyContext.load("java.lang.String")))
      case node: AST.NamedArgument =>
        typed(node.value, context, expected)
      case _ =>
        None
    }

  private def typeBinaryAssignment(
    node: AST.Expression,
    lhs: AST.Expression,
    rhs: AST.Expression,
    binaryKind: BinaryKind,
    context: LocalContext
  ): Option[Term] = {
    val binaryOp = binaryKind match {
      case ADD => new AST.Addition(node.location, lhs, rhs)
      case SUBTRACT => new AST.Subtraction(node.location, lhs, rhs)
      case MULTIPLY => new AST.Multiplication(node.location, lhs, rhs)
      case DIVIDE => new AST.Division(node.location, lhs, rhs)
      case MOD => new AST.Modulo(node.location, lhs, rhs)
      case BIT_AND => new AST.BitAnd(node.location, lhs, rhs)
      case BIT_OR => new AST.BitOr(node.location, lhs, rhs)
      case XOR => new AST.XOR(node.location, lhs, rhs)
      case BIT_SHIFT_L2 => new AST.MathLeftShift(node.location, lhs, rhs)
      case BIT_SHIFT_R2 => new AST.MathRightShift(node.location, lhs, rhs)
      case BIT_SHIFT_R3 => new AST.LogicalRightShift(node.location, lhs, rhs)
      case _ => throw new IllegalArgumentException(s"Invalid compound assignment operator: $binaryKind")
    }
    typeAssignment(new AST.Assignment(node.location, lhs, binaryOp), context)
  }

  /** The element type of an expected `List[E]`, or null if `expected` is not one. */
  private def expectedListElement(expected: Type): Type = expected match {
    case app: AppliedClassType if app.raw.name == "java.util.List" && app.typeArguments.length == 1 =>
      app.typeArguments(0)
    case _ => null
  }

  private def typeListLiteral(node: AST.ListLiteral, context: LocalContext, expected: Type = null): Option[Term] = {
    val typedElements = new Array[Term](node.elements.size)
    val expectedElem = expectedListElement(expected)
    var elementType: Type = null
    var failed = false
    var incompatibleLeft: Type = null
    var incompatibleRight: Type = null
    var index = 0

    node.elements.foreach { element =>
      if (!failed) {
        val typedElement = typed(element, context, expectedElem).orNull
        if (typedElement == null) {
          failed = true
        } else {
          typedElements(index) = typedElement
          val normalizedType = normalizeListElementType(typedElement.`type`)
          if (index == 0) {
            elementType = normalizedType
          } else {
            val merged = mergeListElementType(elementType, normalizedType)
            if (merged == null) {
              failed = true
              incompatibleLeft = elementType
              incompatibleRight = normalizedType
            } else {
              elementType = merged
            }
          }
          index += 1
        }
      }
    }

    if (failed) {
      if (incompatibleLeft != null && incompatibleRight != null) {
        bodyContext.report(INCOMPATIBLE_TYPE, node, incompatibleLeft, incompatibleRight)
      }
      None
    } else {
      val finalElementType =
        if (typedElements.isEmpty) (if (expectedElem != null) expectedElem else bodyContext.rootClass)
        // Target-type the literal: if every element fits the expected element type,
        // adopt it, so `val xs: List[String?] = ["a", null]` builds a List[String?]
        // instead of a List[String] that then fails to assign. Sound because a
        // literal creates a fresh list of that element type (unlike an `as` cast).
        // (Nullable-primitive element types like List[Int?] are not covered here.)
        else if (expectedElem != null && TypeRules.isAssignable(expectedElem, elementType)) expectedElem
        else elementType
      val listType = AppliedClassType(bodyContext.load("java.util.List"), scala.collection.immutable.List(finalElementType))
      Some(new ListLiteral(typedElements, listType))
    }
  }

  private def typeCurrentInstance(node: AST.CurrentInstance, context: LocalContext): Option[Term] = {
    val thisBinding = context.lookup("this")
    if (thisBinding != null) {
      context.recordUsage("this")
      Some(new RefLocal(thisBinding))
    } else if (context.isStatic) {
      bodyContext.report(CURRENT_INSTANCE_NOT_AVAILABLE, node)
      None
    } else if (context.isClosure) {
      // Inside a closure 'this'/'self' denotes the enclosing instance,
      // reached through the closure's this$0 field
      Some(new OuterThis(bodyContext.definition))
    } else {
      Some(new This(node.location, bodyContext.definition))
    }
  }

  private def typeIdentifier(node: AST.Id, context: LocalContext): Option[Term] = {
    val bind = context.lookup(node.name)
    if (bind != null) {
      context.recordUsage(node.name)
      val effectiveType = context.getEffectiveType(node.name)
      if (effectiveType != bind.tp) Some(new AsInstanceOf(new RefLocal(bind), effectiveType))
      else Some(new RefLocal(bind))
    } else {
      // Implicit field access: a bare name with no local binding resolves to a
      // field of the current class -- this.<name> for an instance field, the
      // static field directly for a static one.
      resolveImplicitField(node, context) match {
        case Some(term) =>
          // Smart-cast an implicitly-accessed val field narrowed by a null check.
          context.getFieldNarrowing(node.name) match {
            case Some(nt) if nt != term.`type` => Some(new AsInstanceOf(term, nt))
            case _ => Some(term)
          }
        case None =>
          // A bare name may be a top-level val/var, which lives as a static field
          // on the synthetic top-level class. Resolve it as a static-field read so
          // top-level functions, main, and methods of other classes can all reach it.
          val topStatic = bodyContext.topLevelClass.flatMap { tc =>
            val f = tc.field(node.name)
            if (f != null && (f.modifier & AST.M_STATIC) != 0) Some(new RefStaticField(node.location, tc, f))
            else None
          }
          topStatic match {
            case Some(term) => Some(term)
            case None =>
              // Fields that can't be reached this way (e.g. an instance field from
              // a static method) still suggest the qualified form to use.
              fieldQualificationHint(node.name, context) match {
                case Some(hint) => bodyContext.report(VARIABLE_NOT_FOUND, node, node.name, context.allNames.toArray, hint)
                case None => bodyContext.report(VARIABLE_NOT_FOUND, node, node.name, context.allNames.toArray)
              }
              None
          }
      }
    }
  }

  /**
   * Resolve a bare name as a field of the current class: the static field
   * directly, or the instance field through `this` when not in a static
   * context. None when no such field is reachable.
   */
  private def resolveImplicitField(node: AST.Id, context: LocalContext): Option[Term] = {
    val field = bodyContext.definition.findField(node.name)
    if (field == null) None
    else if ((field.modifier & AST.M_STATIC) != 0)
      Some(new RefStaticField(node.location, bodyContext.definition, field))
    else if (!context.isStatic)
      typeMemberSelection(AST.MemberSelection(node.location, AST.CurrentInstance(node.location), node.name), context)
    else None
  }

  /**
   * The qualified form to suggest when a bare name names a field that implicit
   * access can't reach (an instance field referenced from a static context).
   */
  private def fieldQualificationHint(name: String, context: LocalContext): Option[String] = {
    val field = bodyContext.definition.findField(name)
    if (field == null) None
    else if ((field.modifier & AST.M_STATIC) != 0) Some(s"${bodyContext.definition.name}::$name")
    else if (!context.isStatic) Some(s"this.$name")
    else None
  }

  private def typeUnqualifiedFieldReference(node: AST.UnqualifiedFieldReference, context: LocalContext): Option[Term] =
    if (context.isStatic) {
      bodyContext.report(VARIABLE_NOT_FOUND, node, node.name, context.allNames.toArray)
      None
    } else {
      val selection = AST.MemberSelection(node.location, AST.CurrentInstance(node.location), node.name)
      typeMemberSelection(selection, context)
    }

  /** The (key, value) types of an expected `Map[K, V]`, or (null, null). */
  private def expectedMapKeyValue(expected: Type): (Type, Type) = expected match {
    case app: AppliedClassType if app.raw.name == "java.util.Map" && app.typeArguments.length == 2 =>
      (app.typeArguments(0), app.typeArguments(1))
    case _ => (null, null)
  }

  private def typeMapLiteral(node: AST.MapLiteral, context: LocalContext, expected: Type = null): Option[Term] = {
    val keys = new Array[Term](node.entries.size)
    val values = new Array[Term](node.entries.size)
    val (expectedKey, expectedValue) = expectedMapKeyValue(expected)
    var keyType: Type = null
    var valueType: Type = null
    var failed = false
    var index = 0

    node.entries.foreach { case (keyExpr, valueExpr) =>
      if (!failed) {
        val typedKey = typed(keyExpr, context, expectedKey).orNull
        val typedValue = typed(valueExpr, context, expectedValue).orNull
        if (typedKey == null || typedValue == null) {
          failed = true
        } else {
          keys(index) = typedKey
          values(index) = typedValue
          val normalizedKey = normalizeListElementType(typedKey.`type`)
          val normalizedValue = normalizeListElementType(typedValue.`type`)
          if (index == 0) {
            keyType = normalizedKey
            valueType = normalizedValue
          } else {
            val mergedKey = mergeListElementType(keyType, normalizedKey)
            val mergedValue = mergeListElementType(valueType, normalizedValue)
            if (mergedKey == null) {
              failed = true
              bodyContext.report(INCOMPATIBLE_TYPE, keyExpr, keyType, normalizedKey)
            } else if (mergedValue == null) {
              failed = true
              bodyContext.report(INCOMPATIBLE_TYPE, valueExpr, valueType, normalizedValue)
            } else {
              keyType = mergedKey
              valueType = mergedValue
            }
          }
          index += 1
        }
      }
    }

    if (failed) None
    else {
      val finalKeyType = if (keys.isEmpty) (if (expectedKey != null) expectedKey else bodyContext.rootClass) else keyType
      val finalValueType = if (values.isEmpty) (if (expectedValue != null) expectedValue else bodyContext.rootClass) else valueType
      val mapType = AppliedClassType(bodyContext.load("java.util.Map"), scala.collection.immutable.List(finalKeyType, finalValueType))
      Some(new MapLiteral(keys, values, mapType))
    }
  }

  private def normalizeListElementType(tp: Type): Type =
    tp match {
      case basicType: BasicType => Boxing.boxedType(bodyContext.table, basicType)
      case other => other
    }

  private def mergeListElementType(left: Type, right: Type): Type =
    if (left == null || right == null) null
    else if (left.isBottomType) right
    else if (right.isBottomType) left
    else if (left eq right) left
    else if (left.isNullType) right
    else if (right.isNullType) left
    else if (TypeRules.isSuperType(left, right)) left
    else if (TypeRules.isSuperType(right, left)) right
    else if (!left.isBasicType && !right.isBasicType) bodyContext.rootClass
    else null
}
