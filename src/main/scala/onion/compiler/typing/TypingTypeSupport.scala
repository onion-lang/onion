package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import collection.mutable.{Buffer, Set => MutableSet}

final class TypingTypeSupport(private val typing: Typing) {
  def typesOf(arguments: List[AST.Argument]): Option[List[Type]] = {
    val result = arguments.map { arg =>
      mapFrom(arg.typeRef).map { baseType =>
        if (arg.isVararg) typing.table_.loadArray(baseType, 1) else baseType
      }
    }
    if (result.forall(_.isDefined)) Some(result.flatten) else None
  }

  def mapFrom(typeNode: AST.TypeNode): Option[Type] = mapFrom(typeNode, typing.mapper_)

  /** Resolves a type node; reports CLASS_NOT_FOUND itself, so callers only handle absence. */
  def mapFrom(typeNode: AST.TypeNode, mapper: NameResolver): Option[Type] = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) {
      typing.report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames)
      None
    } else {
      validateTypeApplication(typeNode, mappedType)
      Some(mappedType)
    }
  }

  def openTypeParams[A](scope: TypeParamScope)(block: => A): A = {
    val prev = typing.typeParams_
    typing.setTypeParams(scope)
    try block
    finally typing.setTypeParams(prev)
  }

  def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] = {
    val seen = MutableSet[String]()
    val result = Buffer[TypeParam]()
    for (tp <- nodes) {
      if (seen.contains(tp.name)) {
        typing.report(DUPLICATE_TYPE_PARAMETER, tp, tp.name)
      } else {
        seen += tp.name
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
        result += TypeParam(tp.name, variableType, upper)
      }
    }
    result.toSeq
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
              val upper = rawParams(i).upperBound.getOrElse(typing.rootClass)
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
