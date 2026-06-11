package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

final class ConstructionTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    for {
      target <- typed(node.lhs, context)
      indexRaw <- typed(node.rhs, context)
      result <- {
        if (target.isArrayType) {
          val index = Boxing.tryUnboxToInteger(bodyContext.table, indexRaw)
          if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
            bodyContext.report(INCOMPATIBLE_TYPE, node, BasicType.INT, index.`type`)
            None
          } else Some(new RefArray(target, index))
        } else if (target.isBasicType || target.isNullType) {
          bodyContext.report(INCOMPATIBLE_TYPE, node.lhs, bodyContext.rootClass, target.`type`)
          None
        } else {
          target.`type` match {
            case tv: TypeVariableType if tv.nullability == Nullability.Nullable =>
              // A bare [T] ranges over nullable types: indexing dereferences
              // the receiver, so null must be excluded first
              bodyContext.report(TYPE_PARAMETER_MAY_BE_NULL, node.lhs, tv.displayName)
              None
            case objType: ObjectType =>
              val params = Array(indexRaw)
              tryFindMethod(node, objType, "get", params) match {
                case Left(_) =>
                  bodyContext.report(METHOD_NOT_FOUND, node, target.`type`, "get", types(params))
                  None
                case Right(method) =>
                  // Specialize the element type for generic collections so
                  // xs[i] on a List[Integer] has type Integer, not Object
                  val elementType = TypeSubst.withClassOnly(method.returnType, target.`type`)
                  Some(TypeSubst.withCast(new Call(target, method, params), elementType))
              }
            case other =>
              // e.g. indexing a nullable receiver: xs[i] needs a definite
              // object type; nullable values must be unwrapped first
              bodyContext.report(INVALID_METHOD_CALL_TARGET, node.lhs, other)
              None
          }
        }
      }
    } yield result

  /**
   * Safe indexing: target?[index] yields null when target is null. Arrays
   * use a null-guarded load (SafeRefArray); collections route through a
   * SafeCall to get(), so the element type widens to nullable either way.
   */
  def typeSafeIndexing(node: AST.SafeIndexing, context: LocalContext): Option[Term] =
    for {
      target <- typed(node.lhs, context)
      indexRaw <- typed(node.rhs, context)
      result <- {
        val targetType = target.`type` match {
          case n: NullableType => n.innerType
          case other => other
        }
        if (targetType.isArrayType) {
          val arrayType = targetType.asInstanceOf[ArrayType]
          val index = Boxing.tryUnboxToInteger(bodyContext.table, indexRaw)
          if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
            bodyContext.report(INCOMPATIBLE_TYPE, node, BasicType.INT, index.`type`)
            None
          } else Some(new SafeRefArray(target, index, arrayType))
        } else if (targetType.isBasicType || targetType.isNullType) {
          bodyContext.report(INCOMPATIBLE_TYPE, node.lhs, bodyContext.rootClass, target.`type`)
          None
        } else {
          targetType match {
            case objType: ObjectType =>
              val params = Array(indexRaw)
              tryFindMethod(node, objType, "get", params) match {
                case Left(_) =>
                  bodyContext.report(METHOD_NOT_FOUND, node, target.`type`, "get", types(params))
                  None
                case Right(method) =>
                  val call = new SafeCall(target, method, params)
                  val elementType = TypeSubst.withClassOnly(method.returnType, targetType)
                  Some(TypeSubst.withCast(call, NullableType.of(elementType)))
              }
            case other =>
              bodyContext.report(INVALID_METHOD_CALL_TARGET, node.lhs, other)
              None
          }
        }
      }
    } yield result

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] = {
    val typeRefOpt = typing.mapFrom(node.typeRef, bodyContext.mapper)
    val parameters = typedTerms(node.args.toArray, context)
    if (typeRefOpt.isEmpty || parameters == null) return None
    val resultType = typing.loadArray(typeRefOpt.get, parameters.length)
    Some(new NewArray(resultType, parameters))
  }

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] = {
    val elementTypeOpt = typing.mapFrom(node.typeRef, bodyContext.mapper)
    if (elementTypeOpt.isEmpty) return None
    val elementType = elementTypeOpt.get
    val arrayType = typing.loadArray(elementType, 1)
    val typedValues = node.values.toArray.map { expr =>
      typed(expr, context, elementType).flatMap { t =>
        if (TypeRules.isAssignable(elementType, t.`type`)) Some(t)
        else {
          bodyContext.report(INCOMPATIBLE_TYPE, expr, elementType, t.`type`)
          None
        }
      }
    }
    if (typedValues.exists(_.isEmpty)) return None
    Some(new NewArrayWithValues(arrayType, typedValues.flatten))
  }

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] = {
    val typeRef = typing.mapFrom(node.typeRef) match {
      case Some(ct: ClassType) => ct
      case Some(other) =>
        bodyContext.report(INCOMPATIBLE_TYPE, node, bodyContext.rootClass, other)
        return None
      case None => return None
    }

    // Check if trying to instantiate an abstract class
    val classToCheck = typeRef match {
      case applied: TypedAST.AppliedClassType => applied.raw
      case _ => typeRef
    }
    if (Modifier.isAbstract(classToCheck.modifier)) {
      bodyContext.report(ABSTRACT_CLASS_INSTANTIATION, node, typeRef)
      return None
    }

    // Check for named arguments
    if (hasNamedArguments(node.args)) {
      return typeNewObjectWithNamedArgs(node, typeRef, context)
    }

    // Existing positional argument handling
    val parameters0 = typedTerms(node.args.toArray, context)
    if (parameters0 == null) return None

    val constructors0 = typeRef.findConstructor(parameters0)
    // Exact matching is substitution-blind (an applied Pair[String, Integer]
    // still exposes (A, B)): retry against the substituted signatures with
    // boxing so 'new Pair[String, Integer]("x", 42)' boxes 42
    val (constructors, parameters) =
      if (constructors0.nonEmpty) (constructors0, parameters0)
      else findConstructorWithBoxing(typeRef, parameters0)
    if (constructors.length == 0) {
      // Default-parameter fallback: a constructor with defaults accepts
      // fewer positional arguments than its signature lists
      val defaultsCandidate = typeRef.constructors.exists {
        case cd: ConstructorDefinition =>
          cd.argumentsWithDefaults != null &&
            parameters0.length < cd.argumentsWithDefaults.length &&
            parameters0.length >= cd.minArguments
        case _ => false
      }
      if (defaultsCandidate) return typeNewObjectWithNamedArgs(node, typeRef, context)
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters), typeRef.constructors)
      None
    } else if (constructors.length > 1) {
      bodyContext.report(
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
   * Boxing-aware constructor fallback: substitutes class type arguments into
   * the constructor signatures, matches with primitive boxing, and adapts
   * the argument terms (boxing primitives) for the unique match.
   */
  private def findConstructorWithBoxing(
    typeRef: ClassType,
    parameters: Array[Term]
  ): (Array[ConstructorRef], Array[Term]) = {
    val classSubst = TypeSubstitution.classSubstitution(typeRef)
    def substitutedArgs(c: ConstructorRef): Array[Type] =
      c.getArgs.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    val candidates = typeRef.constructors.filter { c =>
      val formals = substitutedArgs(c)
      formals.length == parameters.length &&
        formals.indices.forall(i => TypeRelations.isAssignableWithBoxing(formals(i), parameters(i).`type`, bodyContext.table))
    }
    if (candidates.length != 1) (candidates, parameters)
    else {
      val formals = substitutedArgs(candidates(0))
      val adapted = parameters.zip(formals).map { (p, f) =>
        if (!f.isBasicType && p.isBasicType) Boxing.boxing(bodyContext.table, p)
        else p
      }
      (candidates, adapted)
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
    val candidates = filterConstructorsByNamedArgs(typeRef.constructors.toIndexedSeq, namedInfo)

    if (candidates.isEmpty) {
      // Type the arguments anyway to provide better error messages
      val parameters = typedTerms(node.args.toArray.filterNot(_.isInstanceOf[AST.NamedArgument]), context)
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, typeRef,
        if (parameters != null) types(parameters) else Array.empty[Type],
        typeRef.constructors)
      return None
    }

    if (candidates.length > 1) {
      bodyContext.report(
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
            bodyContext.report(UNKNOWN_PARAMETER_NAME, named, named.name)
            hasError = true
          } else if (filled(paramIndex)) {
            bodyContext.report(DUPLICATE_ARGUMENT, named, named.name)
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
            bodyContext.report(POSITIONAL_AFTER_NAMED, expr)
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
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, ctor.affiliation,
        argsWithDefaults.map(_.argType), ctor.affiliation.constructors)
      return None
    }

    // Fill missing arguments with default values
    argsWithDefaults.indices.foreach { i =>
      if (!filled(i)) {
        result(i) = argsWithDefaults(i).defaultValue.getOrElse {
          throw new IllegalStateException(s"constructor argument ${argsWithDefaults(i).name} has no default despite the required-argument check")
        }
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
