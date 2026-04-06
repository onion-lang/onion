package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import collection.mutable.{Buffer, Set => MutableSet}

final class TypingTypeSupport(private val typing: Typing) {
  import typing.*

  def typesOf(arguments: List[AST.Argument]): Option[List[Type]] = {
    val result = arguments.map { arg =>
      val baseType = mapFrom(arg.typeRef)
      if (baseType != null && arg.isVararg) table_.loadArray(baseType, 1)
      else baseType
    }
    if (result.forall(_ != null)) Some(result) else None
  }

  def mapFrom(typeNode: AST.TypeNode): Type = mapFrom(typeNode, mapper_)

  def mapFromOpt(typeNode: AST.TypeNode): Option[Type] = mapFromOpt(typeNode, mapper_)

  def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): Type = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) {
      report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames)
    } else {
      validateTypeApplication(typeNode, mappedType)
    }
    mappedType
  }

  def mapFromOpt(typeNode: AST.TypeNode, mapper: NameMapper): Option[Type] = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) {
      report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames)
      None
    } else {
      validateTypeApplication(typeNode, mappedType)
      Some(mappedType)
    }
  }

  def openTypeParams[A](scope: TypeParamScope)(block: => A): A = {
    val prev = typeParams_
    typeParams_ = scope
    try block
    finally typeParams_ = prev
  }

  def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] = {
    val seen = MutableSet[String]()
    val result = Buffer[TypeParam]()
    for (tp <- nodes) {
      if (seen.contains(tp.name)) {
        report(DUPLICATE_TYPE_PARAMETER, tp, tp.name)
      } else {
        seen += tp.name
        val upper = tp.upperBound match {
          case Some(boundNode) =>
            val mapped = mapFrom(boundNode)
            mapped match {
              case ct: ClassType => ct
              case _ =>
                report(INCOMPATIBLE_TYPE, boundNode, rootClass, mapped)
                rootClass
            }
          case None =>
            rootClass
        }
        val variableType = new TypedAST.TypeVariableType(tp.name, upper)
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
              report(TYPE_NOT_GENERIC, typeNode, applied.raw.name)
              return
            }
            if (rawParams.length != applied.typeArguments.length) {
              report(
                TYPE_ARGUMENT_ARITY_MISMATCH,
                typeNode,
                applied.raw.name,
                Integer.valueOf(rawParams.length),
                Integer.valueOf(applied.typeArguments.length)
              )
              return
            }
            val hasError = rawParams.indices.exists { i =>
              val upper = rawParams(i).upperBound.getOrElse(rootClass)
              val arg = applied.typeArguments(i)
              if (arg eq BasicType.VOID) {
                report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeNode, arg.name)
                true
              } else {
                arg match {
                  case wildcard: TypedAST.WildcardType =>
                    val wildcardUpper = wildcard.upperBound
                    if (!TypeRules.isAssignable(upper, wildcardUpper)) {
                      report(INCOMPATIBLE_TYPE, typeNode, upper, wildcardUpper)
                      true
                    } else false
                  case _ =>
                    val checkedArg = boxedTypeArgument(arg)
                    if (!TypeRules.isAssignable(upper, checkedArg)) {
                      report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
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
