package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

import scala.util.boundary
import scala.util.boundary.break

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
    val typeRefOpt = typing.mapFromDeclared(node.typeRef, bodyContext.mapper)
    val parameters = typedTerms(node.args.toArray, context)
    if (typeRefOpt.isEmpty || parameters == null) return None
    val resultType = typing.loadArray(typeRefOpt.get, parameters.length)
    Some(new NewArray(resultType, parameters))
  }

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] = {
    val elementTypeOpt = typing.mapFromDeclared(node.typeRef, bodyContext.mapper)
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

  /**
   * Constructor diamond: when `new C(...)` names a generic class without type
   * arguments and the expected type is a parameterization of that same class,
   * adopt the expected type's arguments (e.g. `val b: Box[String] = new Box("x")`)
   * instead of rejecting the bare type as raw (E0066). Returns None when there is
   * nothing to infer, so the caller falls back to the raw-checking resolution.
   */
  private def diamondType(node: AST.NewObject, expected: Type): Option[ClassType] =
    expected match {
      case exp: AppliedClassType =>
        typing.mapFrom(node.typeRef) match {
          case Some(raw: ClassType) if !raw.isInstanceOf[AppliedClassType] && raw.typeParameters.nonEmpty =>
            if (TypeRelations.sameClass(raw, exp.raw) && exp.typeArguments.length == raw.typeParameters.length)
              Some(AppliedClassType(raw, exp.typeArguments.toList))
            else
              diamondTypeFromSupertype(raw, exp)
          case _ => None
        }
      case _ => None
    }

  /**
   * `new C(...)` where `C` conforms to the expected applied type through a
   * supertype/interface rather than being that exact class -- e.g. a generic
   * ADT enum's singleton case, `Nothing` in `enum Opt[T] { case Some(value: T);
   * case Nothing }`, constructed as `val o: Opt[String] = new Nothing()`.
   * `Nothing` passes its own type parameter straight through to `Opt[T]`, so
   * `T` can be recovered from `exp`'s arguments the same way [[diamondType]]
   * already does for an exact-class match.
   *
   * Only that exact shape is trusted: `raw`'s conforms/extends clause must
   * name `exp`'s class with each argument a bare, distinct reference to one
   * of `raw`'s own type parameters. A concrete type, a nested application, or
   * a dropped/repeated parameter falls through to `None`, leaving the
   * existing raw-type diagnostic in place rather than guessing.
   */
  private def diamondTypeFromSupertype(raw: ClassType, exp: AppliedClassType): Option[ClassType] = {
    def rawOf(ct: ClassType): ClassType = ct match {
      case a: AppliedClassType => a.raw
      case c => c
    }
    val parents = Option(raw.superClass).toSeq ++ raw.interfaces
    parents.collectFirst {
      case p: AppliedClassType
        if TypeRelations.sameClass(rawOf(p), exp.raw) && p.typeArguments.length == exp.typeArguments.length => p
    }.flatMap { parent =>
      val bindings = parent.typeArguments.zip(exp.typeArguments).foldLeft(Option(Map.empty[String, Type])) {
        case (Some(acc), (tv: TypeVariableType, arg)) if raw.typeParameters.exists(_.name == tv.name) && !acc.contains(tv.name) =>
          Some(acc + (tv.name -> arg))
        case _ => None
      }
      bindings.flatMap { b =>
        if (raw.typeParameters.forall(tp => b.contains(tp.name)))
          Some(AppliedClassType(raw, raw.typeParameters.map(tp => b(tp.name)).toList))
        else None
      }
    }
  }

  /**
   * The bare generic class named by `new C(...)`, or None when `C` is not a
   * generic class written without type arguments. Uses `mapFrom` rather than
   * `mapFromDeclared` so probing does not itself report E0066 for the very raw
   * type we are about to infer arguments for.
   */
  private def bareGenericTarget(node: AST.NewObject): Option[ClassType] =
    typing.mapFrom(node.typeRef) match {
      case Some(raw: ClassType)
        if !raw.isInstanceOf[AppliedClassType] && raw.typeParameters.nonEmpty => Some(raw)
      case _ => None
    }

  /**
   * Constructor diamond from the arguments: when `new C(...)` names a generic
   * class without type arguments and no expected type supplies them, infer them
   * from the constructor arguments the way a generic *method* call already does
   * (`id("hi")` infers T = String), instead of rejecting the bare type as raw
   * (E0066) -- issue #305.
   *
   * The inference reuses the generic-method engine by describing the candidate
   * constructor as a synthetic method carrying the *class's* type parameters, so
   * both paths bind type variables by exactly the same rules.
   *
   * Only unambiguous cases are inferred: every candidate constructor that
   * matches the argument shape must agree on the resulting type arguments, and
   * every class type parameter must actually be constrained by an argument.
   * Anything else returns None, leaving the existing raw-type diagnostic in
   * place rather than silently guessing.
   */
  private def diamondTypeFromArguments(node: AST.NewObject, context: LocalContext): Option[ClassType] = {
    // A closure argument cannot be typed before its target type is known, and
    // named arguments take a separate resolution path; both keep the existing
    // behavior rather than participating in inference.
    if (node.args.exists(_.isInstanceOf[AST.ClosureExpression])) return None
    if (hasNamedArguments(node.args)) return None

    bareGenericTarget(node).flatMap { raw =>
      boundary[Option[ClassType]] {
        // Probe-type the arguments: a genuine error here is reported once by the
        // real resolution path below, not twice by this speculative pass.
        val args = typing.withSuppressedReporting(typedTerms(node.args.toArray, context))
        if (args == null || args.exists(_ == null)) break(None)

        val typeParams = raw.typeParameters
        val candidates = raw.constructors.collect {
          case cd: ConstructorDefinition
            if cd.getArgs.length == args.length ||
              (cd.argumentsWithDefaults != null && args.length < cd.argumentsWithDefaults.length && args.length >= cd.minArguments) => cd
        }
        if (candidates.isEmpty) break(None)

        val solutions: List[List[Type]] = candidates.toList.flatMap { candidate =>
          val synthetic = new MethodDefinition(
            node.location,
            candidate.modifier,
            raw,
            "<init>",
            candidate.getArgs.take(args.length),
            raw,
            null,
            typeParams
          )
          val bindings = typing.withSuppressedReporting {
            GenericMethodTypeArguments.inferWithoutDefaults(
              typing, node, synthetic, args, scala.collection.immutable.Map.empty)
          }
          // Every class type parameter must be pinned by an argument; a partially
          // inferred type would otherwise silently default the rest to their bound.
          if (typeParams.forall(tp => bindings.contains(tp.name)))
            Some(typeParams.map(tp => bindings(tp.name)).toList)
          else None
        }.distinct

        solutions match {
          case only :: Nil => Some(AppliedClassType(raw, only))
          case _ => None
        }
      }
    }
  }

  def typeNewObject(node: AST.NewObject, context: LocalContext, expected: Type = null): Option[Term] = boundary {
    val typeRef = diamondType(node, expected).orElse(diamondTypeFromArguments(node, context)).getOrElse {
      typing.mapFromDeclared(node.typeRef) match {
        case Some(ct: ClassType) => ct
        case Some(other) =>
          bodyContext.report(INCOMPATIBLE_TYPE, node, bodyContext.rootClass, other)
          break(None)
        case None => break(None)
      }
    }

    // Check if trying to instantiate an abstract class
    val classToCheck = typeRef match {
      case applied: TypedAST.AppliedClassType => applied.raw
      case _ => typeRef
    }
    if (Modifier.isAbstract(classToCheck.modifier)) {
      bodyContext.report(ABSTRACT_CLASS_INSTANTIATION, node, typeRef)
      break(None)
    }

    // Check for named arguments
    if (hasNamedArguments(node.args)) {
      break(typeNewObjectWithNamedArgs(node, typeRef, context))
    }

    // A lambda argument can't take its final type until its target type (the
    // constructor parameter) is known -- in particular a Java functional
    // interface (SAM) target, or a zero-parameter `() -> ...`. Resolve the
    // constructor from the other arguments first, then type the closures against
    // its parameters -- mirroring the bidirectional inference the method-call
    // path performs for every closure argument.
    val untypedClosureIndices = node.args.zipWithIndex.collect {
      case (_: AST.ClosureExpression, i) => i
    }.toSet
    if (untypedClosureIndices.nonEmpty) {
      resolveConstructorForClosures(node, typeRef, context, untypedClosureIndices) match {
        case Some(term) => break(Some(term))
        case None => // ambiguous/unresolved: fall through to the eager path, which reports E0052 / constructor-not-found
      }
    }

    // Existing positional argument handling
    val parameters0 = typedTerms(node.args.toArray, context)
    if (parameters0 == null) break(None)

    val constructors0 = typeRef.findConstructor(parameters0)
    // Exact matching is substitution-blind (an applied Pair[String, Integer]
    // still exposes (A, B)): retry against the substituted signatures with
    // boxing so 'new Pair[String, Integer]("x", 42)' boxes 42
    val constructors = if (constructors0.nonEmpty) constructors0 else findConstructorWithBoxing(typeRef, parameters0)
    val parameters = parameters0
    // Guard the substitution-blind exact match: for an applied generic type the
    // matched constructor must accept the arguments under the type-argument
    // substitution (T -> String), not merely under the erased bound (Object).
    // Otherwise `new Box[String](aStringBuilder)` compiled and threw a runtime
    // ClassCastException — a type-safety hole the instance-method path does not have.
    val substitutionValid = typeRef match {
      case applied: TypedAST.AppliedClassType if constructors.length == 1 =>
        val classSubst = TypeSubstitution.classSubstitution(applied)
        val formals = constructors(0).getArgs.map(t =>
          TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
        // Only reject on an arity-matched signature; differing arity is handled
        // by the vararg/default-parameter paths below, so leave it to them.
        formals.length != parameters.length ||
          formals.indices.forall(i => TypeRelations.isAssignableWithBoxing(formals(i), parameters(i).`type`, bodyContext.table))
      case _ => true
    }
    if (!substitutionValid) {
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters), typeRef.constructors)
      break(None)
    }
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
      if (defaultsCandidate) break(typeNewObjectWithNamedArgs(node, typeRef, context))
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
    } else if (!MemberAccess.isMemberAccessible(constructors(0), bodyContext.definition)) {
      // A private/protected constructor was resolvable but is not accessible here;
      // previously this compiled and threw IllegalAccessError at runtime.
      bodyContext.report(METHOD_NOT_ACCESSIBLE, node, constructors(0).affiliation, "new", constructors(0).getArgs, bodyContext.definition)
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
          val classSubst = TypeSubstitution.classSubstitution(applied)
          val formals = constructors(0).getArgs.map(t =>
            TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
          Some(new NewObject(appliedCtor, adaptToFormals(parameters, formals)))
        case _ =>
          Some(new NewObject(constructors(0), adaptToFormals(parameters, constructors(0).getArgs)))
      }
    }
  }

  /**
   * Adapts already-matched arguments to their constructor's formal types:
   * boxes a primitive argument for a non-primitive formal, and inserts an
   * explicit numeric conversion (widening or constant-narrowing alike)
   * whenever both sides are primitive but differ. A resolved match can need
   * either even outside the boxing-fallback path -- `record Game(price:
   * Double)` called as `new Game(99)` matches directly via `findConstructor`'s
   * own widening-aware comparison, and leaving that `Int` argument
   * unconverted left codegen pushing a 1-slot value where the constructor's
   * descriptor declares a 2-slot `double`, corrupting the JVM stack map
   * frames (`NegativeArraySizeException` in ASM's `Frame.merge`, or a
   * `VerifyError` at class-load time). Found via `MutationFuzzSpec` mutating
   * a decimal literal down to an `Int` in `run/GameStore.on`.
   */
  private def adaptToFormals(parameters: Array[Term], formals: Array[Type]): Array[Term] =
    if (parameters.length != formals.length) parameters
    else parameters.zip(formals).map { (p, f) =>
      if (!f.isBasicType && p.isBasicType) Boxing.boxing(bodyContext.table, p)
      else f match {
        case bt: BasicType if p.isBasicType && bt != p.`type` => new AsInstanceOf(p.location, p, bt)
        case _ => p
      }
    }

  /** Whether `actual` can fill a parameter declared `formal`, either through
    * ordinary boxing-aware assignability or -- for a literal like `-3` against
    * a `Byte`/`Short`/`Char` formal -- constant narrowing (issue #374). */
  private def formalAccepts(formal: Type, actual: Term): Boolean =
    TypeRelations.isAssignableWithBoxing(formal, actual.`type`, bodyContext.table) ||
      (formal match {
        case bt: BasicType => ConstantNarrowing.constantIntOf(actual).exists(v => ConstantNarrowing.fits(bt, v))
        case _ => false
      })

  /**
   * Boxing-aware constructor fallback: substitutes class type arguments into
   * the constructor signatures and matches with primitive boxing or constant
   * narrowing. Argument adaptation (boxing, numeric conversion) happens once
   * the caller has settled on the unique match, in `adaptToFormals`.
   */
  private def findConstructorWithBoxing(
    typeRef: ClassType,
    parameters: Array[Term]
  ): Array[ConstructorRef] = {
    val classSubst = TypeSubstitution.classSubstitution(typeRef)
    def substitutedArgs(c: ConstructorRef): Array[Type] =
      c.getArgs.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    typeRef.constructors.filter { c =>
      val formals = substitutedArgs(c)
      formals.length == parameters.length &&
        formals.indices.forall(i => formalAccepts(formals(i), parameters(i)))
    }
  }

  /**
   * Resolve a constructor when some arguments are lambdas with untyped
   * parameters: type the non-closure arguments, pick the unique constructor
   * that matches on arity and those arguments, then type each closure against
   * the constructor's corresponding parameter type. Returns None (to fall back
   * to eager handling) when resolution is ambiguous or finds no single match.
   */
  /** Whether a lambda with `arity` parameters can be converted to `formal` --
    * i.e. `formal` is an onion.FunctionN type or a Java functional interface
    * (a single abstract non-Object method) whose SAM takes `arity` arguments.
    * Used to disambiguate constructor overloads at a closure argument position
    * (e.g. Thread(Runnable) vs Thread(String) for `new Thread(() -> ...)`). */
  private def closureCanTarget(formal: Type, arity: Int): Boolean = formal match {
    case ct: ClassType =>
      val raw = ct match { case a: AppliedClassType => a.raw; case _ => ct }
      val name = raw.name
      if (name != null && name.matches("""onion\.Function\d+""")) true
      else {
        def isPublicObjectMethod(m: Method): Boolean = m.name match {
          case "equals" => m.arguments.length == 1 && m.arguments(0).name == "java.lang.Object"
          case "hashCode" | "toString" => m.arguments.isEmpty
          case _ => false
        }
        val abstracts = raw.methods.filter(m => Modifier.isAbstract(m.modifier) && !isPublicObjectMethod(m))
        abstracts.map(_.name).distinct.length == 1 && abstracts.exists(_.arguments.length == arity)
      }
    case _ => false
  }

  private def resolveConstructorForClosures(
    node: AST.NewObject,
    typeRef: ClassType,
    context: LocalContext,
    closureIndices: Set[Int]
  ): Option[Term] = boundary {
    val args = node.args.toArray
    val classSubst = TypeSubstitution.classSubstitution(typeRef)
    def substitutedArgs(c: ConstructorRef): Array[Type] =
      c.getArgs.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))

    val prelim = new Array[Term](args.length)
    for (i <- args.indices if !closureIndices.contains(i)) {
      typed(args(i), context) match {
        case Some(t) => prelim(i) = t
        case None => break(None)
      }
    }

    val candidates = typeRef.constructors.filter { c =>
      val formals = substitutedArgs(c)
      formals.length == args.length &&
        args.indices.forall { i =>
          if (closureIndices.contains(i))
            closureCanTarget(formals(i), args(i).asInstanceOf[AST.ClosureExpression].args.length)
          else
            TypeRelations.isAssignableWithBoxing(formals(i), prelim(i).`type`, bodyContext.table)
        }
    }
    if (candidates.length != 1) break(None)

    val ctor = candidates(0)
    val formals = substitutedArgs(ctor)
    val finalParams = new Array[Term](args.length)
    for (i <- args.indices) {
      if (closureIndices.contains(i)) {
        typed(args(i), context, formals(i)) match {
          case Some(t) => finalParams(i) = t
          case None => break(None)
        }
      } else {
        val p = prelim(i)
        finalParams(i) = if (!formals(i).isBasicType && p.isBasicType) Boxing.boxing(bodyContext.table, p) else p
      }
    }

    typeRef match {
      case applied: TypedAST.AppliedClassType =>
        val appliedCtor = new TypedAST.ConstructorRef {
          def modifier: Int = ctor.modifier
          def affiliation: TypedAST.ClassType = applied
          def name: String = ctor.name
          def getArgs: Array[TypedAST.Type] = ctor.getArgs
        }
        Some(new NewObject(appliedCtor, finalParams))
      case _ =>
        Some(new NewObject(ctor, finalParams))
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
      val unknownNamed = typeRef.constructors.collectFirst { case cd: ConstructorDefinition => cd } match {
        case Some(_) =>
          val paramNameSets = typeRef.constructors.collect {
            case cd: ConstructorDefinition => cd.argumentsWithDefaults.map(_.name).toSet
          }
          node.args.collectFirst {
            case named: AST.NamedArgument if !paramNameSets.exists(_.contains(named.name)) => named
          }
        case None => None
      }
      unknownNamed match {
        case Some(named) =>
          val paramNames = typeRef.constructors.collect {
            case cd: ConstructorDefinition => cd.argumentsWithDefaults.map(_.name)
          }.flatten.distinct.toArray
          bodyContext.report(UNKNOWN_PARAMETER_NAME, named, named.name, paramNames)
        case None =>
          // Type the arguments anyway to provide better error messages
          val parameters = typedTerms(node.args.toArray.filterNot(_.isInstanceOf[AST.NamedArgument]), context)
          bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, typeRef,
            if (parameters != null) types(parameters) else Array.empty[Type],
            typeRef.constructors)
      }
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
            bodyContext.report(UNKNOWN_PARAMETER_NAME, named, named.name, paramNames)
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
