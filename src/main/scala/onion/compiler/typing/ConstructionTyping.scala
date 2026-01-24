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
    if (typeRef == null) return None

    // Check if trying to instantiate an abstract class
    val classToCheck = typeRef match {
      case applied: TypedAST.AppliedClassType => applied.raw
      case _ => typeRef
    }
    if (Modifier.isAbstract(classToCheck.modifier)) {
      report(ABSTRACT_CLASS_INSTANTIATION, node, typeRef)
      return None
    }

    // Check for named arguments
    if (hasNamedArguments(node.args)) {
      return typeNewObjectWithNamedArgs(node, typeRef, context)
    }

    // Existing positional argument handling
    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null) return None

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

  /**
   * Handle constructor call with named arguments
   */
  private def typeNewObjectWithNamedArgs(
    node: AST.NewObject,
    typeRef: ClassType,
    context: LocalContext
  ): Option[Term] = {
    // Extract named argument info
    val namedInfo = extractNamedArgInfo(node.args)
    val (positionalCount, namedNames) = namedInfo

    // Filter constructors by named argument compatibility
    val candidates = filterConstructorsByNamedArgs(typeRef.constructors, namedInfo)

    if (candidates.isEmpty) {
      // Type the arguments anyway to provide better error messages
      val parameters = typedTerms(node.args.toArray.filterNot(_.isInstanceOf[AST.NamedArgument]), context)
      report(CONSTRUCTOR_NOT_FOUND, node, typeRef,
        if (parameters != null) types(parameters) else Array.empty[Type],
        typeRef.constructors)
      return None
    }

    if (candidates.length > 1) {
      report(
        AMBIGUOUS_CONSTRUCTOR,
        node,
        Array[AnyRef](candidates(0).affiliation, candidates(0).getArgs),
        Array[AnyRef](candidates(1).affiliation, candidates(1).getArgs)
      )
      return None
    }

    val ctor = candidates.head.asInstanceOf[ConstructorDefinition]

    // Process named arguments and fill defaults
    processNamedArgsForConstructor(node, node.args, ctor, context).map { params =>
      typeRef match {
        case applied: TypedAST.AppliedClassType =>
          val appliedCtor = new TypedAST.ConstructorRef {
            def modifier: Int = ctor.modifier
            def affiliation: TypedAST.ClassType = applied
            def name: String = ctor.name
            def getArgs: Array[TypedAST.Type] = ctor.getArgs
          }
          new NewObject(appliedCtor, params)
        case _ =>
          new NewObject(ctor, params)
      }
    }
  }

  /**
   * Check if argument list contains named arguments
   */
  private def hasNamedArguments(args: List[AST.Expression]): Boolean =
    args.exists(_.isInstanceOf[AST.NamedArgument])

  /**
   * Extract (positionalCount, namedNames) from argument list
   */
  private def extractNamedArgInfo(args: List[AST.Expression]): (Int, Set[String]) = {
    var positionalCount = 0
    val namedNames = scala.collection.mutable.Set[String]()
    args.foreach {
      case named: AST.NamedArgument => namedNames += named.name
      case _ => positionalCount += 1
    }
    (positionalCount, namedNames.toSet)
  }

  /**
   * Filter constructors that can accept the given named arguments
   */
  private def filterConstructorsByNamedArgs(
    constructors: Seq[ConstructorRef],
    namedInfo: (Int, Set[String])
  ): Seq[ConstructorRef] = {
    val (positionalCount, namedNames) = namedInfo
    constructors.filter {
      case cd: ConstructorDefinition =>
        val argsWithDefaults = cd.argumentsWithDefaults
        val paramNames = argsWithDefaults.map(_.name).toSet
        // All named arguments must exist as parameters
        namedNames.subsetOf(paramNames) &&
        // Total arguments must not exceed parameter count
        positionalCount + namedNames.size <= argsWithDefaults.length &&
        // Arguments provided must meet minimum required
        positionalCount + namedNames.size >= cd.minArguments
      case _ =>
        // Non-ConstructorDefinition (e.g., from Java classes) - no named arg support
        namedNames.isEmpty
    }
  }

  /**
   * Process named arguments: reorder and fill defaults
   */
  private def processNamedArgsForConstructor(
    node: AST.Node,
    args: List[AST.Expression],
    ctor: ConstructorDefinition,
    context: LocalContext
  ): Option[Array[Term]] = {
    val argsWithDefaults = ctor.argumentsWithDefaults
    val paramNames = argsWithDefaults.map(_.name)
    val result = new Array[Term](argsWithDefaults.length)
    val filled = new Array[Boolean](argsWithDefaults.length)

    var positionalIndex = 0
    var sawNamed = false
    var hasError = false

    // Process positional and named arguments
    args.foreach { arg =>
      arg match {
        case named: AST.NamedArgument =>
          sawNamed = true
          // Find parameter by name
          val paramIndex = paramNames.indexOf(named.name)
          if (paramIndex < 0) {
            report(UNKNOWN_PARAMETER_NAME, named, named.name)
            hasError = true
          } else if (filled(paramIndex)) {
            report(DUPLICATE_ARGUMENT, named, named.name)
            hasError = true
          } else {
            // Type the value
            typed(named.value, context) match {
              case Some(term) =>
                result(paramIndex) = term
                filled(paramIndex) = true
              case None =>
                hasError = true
            }
          }

        case expr =>
          // Positional argument
          if (sawNamed) {
            report(POSITIONAL_AFTER_NAMED, expr)
            hasError = true
          } else if (positionalIndex >= argsWithDefaults.length) {
            // Too many arguments - type anyway for error reporting
            typed(expr, context)
            positionalIndex += 1
          } else {
            typed(expr, context) match {
              case Some(term) =>
                result(positionalIndex) = term
                filled(positionalIndex) = true
                positionalIndex += 1
              case None =>
                hasError = true
                positionalIndex += 1
            }
          }
      }
    }

    if (hasError) return None

    // Check all required arguments are filled
    val missingRequired = argsWithDefaults.indices.find(i => !filled(i) && argsWithDefaults(i).defaultValue.isEmpty)
    if (missingRequired.isDefined) {
      val missingName = argsWithDefaults(missingRequired.get).name
      report(CONSTRUCTOR_NOT_FOUND, node, ctor.affiliation,
        argsWithDefaults.map(_.argType), ctor.affiliation.constructors)
      return None
    }

    // Fill missing arguments with default values
    argsWithDefaults.indices.foreach { i =>
      if (!filled(i)) {
        result(i) = argsWithDefaults(i).defaultValue.get
        filled(i) = true
      }
    }

    Some(result)
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
