package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

final class ConstructionTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    for {
      target <- typed(node.lhs, context)
      indexRaw <- typed(node.rhs, context)
      result <- {
        if (target.isArrayType) {
          val index = Boxing.tryUnboxToInteger(table_, indexRaw)
          if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
            report(INCOMPATIBLE_TYPE, node, BasicType.INT, index.`type`)
            None
          } else Some(new RefArray(target, index))
        } else if (target.isBasicType) {
          report(INCOMPATIBLE_TYPE, node.lhs, rootClass, target.`type`)
          None
        } else {
          val params = Array(indexRaw)
          tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "get", params) match {
            case Left(_) =>
              report(METHOD_NOT_FOUND, node, target.`type`, "get", types(params))
              None
            case Right(method) =>
              Some(new Call(target, method, params))
          }
        }
      }
    } yield result

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] = {
    val typeRef = mapFrom(node.typeRef, mapper_)
    val parameters = typedTerms(node.args.toArray, context)
    if (typeRef == null || parameters == null) return None
    val resultType = loadArray(typeRef, parameters.length)
    Some(new NewArray(resultType, parameters))
  }

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] = {
    val elementType = mapFrom(node.typeRef, mapper_)
    if (elementType == null) return None
    val arrayType = loadArray(elementType, 1)
    val typedValues = node.values.toArray.map { expr =>
      typed(expr, context, elementType).flatMap { t =>
        if (TypeRules.isAssignable(elementType, t.`type`)) Some(t)
        else {
          report(INCOMPATIBLE_TYPE, expr, elementType, t.`type`)
          None
        }
      }
    }
    if (typedValues.exists(_.isEmpty)) return None
    Some(new NewArrayWithValues(arrayType, typedValues.flatten))
  }

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null || typeRef == null) return None

    // Check if trying to instantiate an abstract class
    val classToCheck = typeRef match {
      case applied: TypedAST.AppliedClassType => applied.raw
      case _ => typeRef
    }
    if (Modifier.isAbstract(classToCheck.modifier)) {
      report(ABSTRACT_CLASS_INSTANTIATION, node, typeRef)
      return None
    }

    val constructors = typeRef.findConstructor(parameters)
    if (constructors.length == 0) {
      report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters), typeRef.constructors)
      None
    } else if (constructors.length > 1) {
      report(
        AMBIGUOUS_CONSTRUCTOR,
        node,
        Array[AnyRef](constructors(0).affiliation, constructors(0).getArgs),
        Array[AnyRef](constructors(1).affiliation, constructors(1).getArgs)
      )
      None
    } else {
      typeRef match {
        case applied: TypedAST.AppliedClassType =>
          val appliedCtor = new TypedAST.ConstructorRef {
            def modifier: Int = constructors(0).modifier
            def affiliation: TypedAST.ClassType = applied
            def name: String = constructors(0).name
            def getArgs: Array[TypedAST.Type] = constructors(0).getArgs
          }
          Some(new NewObject(appliedCtor, parameters))
        case _ =>
          Some(new NewObject(constructors(0), parameters))
      }
    }
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] =
    body.typedTerms(nodes, context)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)
}
