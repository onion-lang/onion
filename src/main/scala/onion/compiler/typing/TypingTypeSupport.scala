package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import collection.mutable.{Buffer, Set => MutableSet}

final class TypingTypeSupport(private val typing: Typing) {
  def typesOf(arguments: List[AST.Argument]): Option[List[Type]] = {
    val result = arguments.map { arg =>
      mapFrom(arg.typeRef, typing.mapper_, banRaw = true).map { baseType =>
        if (arg.isVararg) typing.table_.loadArray(baseType, 1) else baseType
      }
    }
    if (result.forall(_.isDefined)) Some(result.flatten) else None
  }

  def mapFrom(typeNode: AST.TypeNode): Option[Type] = mapFrom(typeNode, typing.mapper_)

  /** Resolves a type node; reports CLASS_NOT_FOUND itself, so callers only handle absence. */
  def mapFrom(typeNode: AST.TypeNode, mapper: NameResolver): Option[Type] =
    mapFrom(typeNode, mapper, banRaw = false)

  /**
   * Resolves a type node. When `banRaw` is set, a reference to a generic type
   * that omits its type arguments (a raw type such as `List` or `ArrayList`) is
   * reported as an error: Onion forbids raw generic types in declared/created
   * positions. Erasure contexts (`is`, `as`, static-call receivers, `catch`)
   * keep raw types by leaving `banRaw` off, since `x is List[Int]` is not
   * expressible under erasure.
   */
  def mapFrom(typeNode: AST.TypeNode, mapper: NameResolver, banRaw: Boolean): Option[Type] = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) {
      typing.report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames)
      None
    } else {
      validateTypeApplication(typeNode, mappedType)
      if (banRaw) reportRawTypes(typeNode, typeNode.desc, mapper)
      Some(mappedType)
    }
  }

  /**
   * Walks a source type descriptor and reports RAW_TYPE_NOT_ALLOWED for every
   * bare reference to a generic type. A reference wrapped in a
   * `ParameterizedType` (its component) is applied and thus fine; its arguments
   * are still checked recursively. Type variables and already-applied types are
   * not raw.
   */
  private def reportRawTypes(node: AST.TypeNode, desc: AST.TypeDescriptor, mapper: NameResolver): Unit =
    desc match {
      case ref: AST.ReferenceType =>
        mapper.map(ref) match {
          case ct: ClassType
            if !ct.isInstanceOf[TypedAST.TypeVariableType]
              && !ct.isInstanceOf[TypedAST.AppliedClassType]
              && ct.typeParameters.nonEmpty =>
            typing.report(RAW_TYPE_NOT_ALLOWED, node, ref.name)
          case _ =>
        }
      case AST.ParameterizedType(_, params) =>
        params.foreach(reportRawTypes(node, _, mapper))
      case AST.ArrayType(component) =>
        reportRawTypes(node, component, mapper)
      case AST.NullableType(inner) =>
        reportRawTypes(node, inner, mapper)
      case AST.FunctionType(params, result) =>
        params.foreach(reportRawTypes(node, _, mapper))
        reportRawTypes(node, result, mapper)
      case AST.WildcardType(upper, lower) =>
        upper.foreach(reportRawTypes(node, _, mapper))
        lower.foreach(reportRawTypes(node, _, mapper))
      case _ =>
    }

  def openTypeParams[A](scope: TypeParamScope)(block: => A): A = {
    val prev = typing.typeParams_
    typing.setTypeParams(scope)
    try block
    finally typing.setTypeParams(prev)
  }

  def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] = {
    val seen = MutableSet[String]()
    val distinct = Buffer[AST.TypeParameter]()
    for (tp <- nodes) {
      if (seen.contains(tp.name)) typing.report(DUPLICATE_TYPE_PARAMETER, tp, tp.name)
      else { seen += tp.name; distinct += tp }
    }
    // Phase 1: register placeholder type variables so a bound may reference any
    // of these parameters, including itself (F-bounds: T extends Comparable[T]).
    val placeholders = distinct.map { tp =>
      TypeParam(tp.name, new TypedAST.TypeVariableType(tp.name, typing.rootClass, Nullability.Platform), typing.rootClass)
    }.toSeq
    // Phase 2: resolve each real bound with all the parameters in scope.
    openTypeParams(typing.typeParams_ ++ placeholders) {
      distinct.map { tp =>
        // Nullability: bare [T] means T ranges over nullable types too;
        // an explicit non-null bound [T extends B] restricts T to non-null
        // types; [T extends B?] opts back into nullable with bound B
        val (upper, nullability) = tp.upperBound match {
          case Some(boundNode) =>
            mapFrom(boundNode) match {
              case Some(ct: ClassType) => (ct, Nullability.NonNull)
              case Some(n: NullableType) =>
                n.innerType match {
                  case ct: ClassType => (ct, Nullability.Nullable)
                  case other =>
                    typing.report(INCOMPATIBLE_TYPE, boundNode, typing.rootClass, other)
                    (typing.rootClass, Nullability.Nullable)
                }
              case Some(mapped) =>
                typing.report(INCOMPATIBLE_TYPE, boundNode, typing.rootClass, mapped)
                (typing.rootClass, Nullability.NonNull)
              case None =>
                (typing.rootClass, Nullability.NonNull)
            }
          case None =>
            (typing.rootClass, Nullability.Nullable)
        }
        val variableType = new TypedAST.TypeVariableType(tp.name, upper, nullability)
        // Resolve type-class context bounds (`[T: Numeric]`). mapFrom already
        // reports E0003 for an unknown trait, so `[T: Unknown]` is no longer
        // silently accepted. The resolved traits feed dictionary-parameter
        // derivation for the eventual dictionary passing.
        val constraintTypes = tp.constraints.flatMap { c =>
          mapFrom(c) match {
            case Some(ct: ClassType) => Some(ct)
            case _ => None
          }
        }
        TypeParam(tp.name, variableType, upper, constraintTypes)
      }.toSeq
    }
  }

  private def validateTypeApplication(typeNode: AST.TypeNode, mappedType: Type): Unit = {
    typeNode.desc match {
      case AST.ParameterizedType(_, _) | AST.FunctionType(_, _) =>
        mappedType match {
          case applied: TypedAST.AppliedClassType =>
            val rawParams = applied.raw.typeParameters
            if (rawParams.isEmpty) {
              typing.report(TYPE_NOT_GENERIC, typeNode, applied.raw.name)
              return
            }
            if (rawParams.length != applied.typeArguments.length) {
              typing.report(
                TYPE_ARGUMENT_ARITY_MISMATCH,
                typeNode,
                applied.raw.name,
                Integer.valueOf(rawParams.length),
                Integer.valueOf(applied.typeArguments.length)
              )
              return
            }
            val hasError = rawParams.indices.exists { i =>
              val param = rawParams(i)
              val upper = param.upperBound.getOrElse(typing.rootClass)
              val arg = applied.typeArguments(i)
              if (arg eq BasicType.VOID) {
                typing.report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeNode, arg.name)
                true
              } else {
                arg match {
                  case wildcard: TypedAST.WildcardType =>
                    val wildcardUpper = wildcard.upperBound
                    if (!TypeRules.isAssignable(upper, wildcardUpper)) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, wildcardUpper)
                      true
                    } else false
                  // Nullability is checked apart from assignability: the
                  // Object top-type rule accepts T?, so a non-null Object
                  // bound can't reject String? through isAssignable alone
                  case n: TypedAST.NullableType =>
                    if (param.nullability == Nullability.NonNull) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                      true
                    } else if (!TypeRules.isAssignable(upper, typing.boxedTypeArgument(n.innerType))) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                      true
                    } else false
                  case _ =>
                    val checkedArg = typing.boxedTypeArgument(arg)
                    if (!TypeRules.isAssignable(upper, checkedArg)) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                      true
                    } else false
                }
              }
            }
            if (hasError) return
          case _ =>
        }
      case _ =>
    }
  }
}
