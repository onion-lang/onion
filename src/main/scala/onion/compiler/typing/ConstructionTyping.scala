package onion.compiler.typing

import ArgumentHelpers.hasNamedArguments

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
            case nullable: NullableType =>
              // `b[i]` where `b: List[Int]?`: indexing dereferences the
              // receiver just like `b.field` does, so it gets the same
              // null-safety error (E0070) instead of the generic
              // INVALID_METHOD_CALL_TARGET (E0041) a genuinely invalid
              // target (e.g. a type parameter) gets -- mirrors the member
              // access path (MemberSelectionResolutionSupport.normalizeTarget).
              bodyContext.report(NULLABLE_MEMBER_ACCESS, node.lhs, nullable.displayName, "indexing")
              None
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
    val untypedArgs = node.args.toArray
    val parameters = typedTerms(untypedArgs, context)
    if (typeRefOpt.isEmpty || parameters == null) return None
    val nullableIndex = parameters.indexWhere(_.`type`.isInstanceOf[NullableType])
    if (nullableIndex >= 0) {
      val nullable = parameters(nullableIndex).`type`.asInstanceOf[NullableType]
      bodyContext.report(NULLABLE_MEMBER_ACCESS, untypedArgs(nullableIndex), nullable.displayName, "arraySize")
      return None
    }
    val sizes = parameters.map(Boxing.tryUnboxToInteger(bodyContext.table, _))
    val badIndex = sizes.indexWhere(size => !(size.isBasicType && size.`type`.asInstanceOf[BasicType].isInteger))
    if (badIndex >= 0) {
      bodyContext.report(INCOMPATIBLE_TYPE, untypedArgs(badIndex), BasicType.INT, sizes(badIndex).`type`)
      return None
    }
    val resultType = typing.loadArray(typeRefOpt.get, sizes.length)
    Some(new NewArray(resultType, sizes))
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
        case Some(bt: BasicType) =>
          // `new Double(...)`, `new Int(...)`, etc. -- a capitalized primitive name
          // resolves here to its BasicType, not a class, so without this check the
          // mismatch fell through to the generic INCOMPATIBLE_TYPE below with nothing
          // connecting "type Object is expected" back to the actual mistake.
          bodyContext.report(CANNOT_INSTANTIATE_PRIMITIVE_TYPE, node, bt)
          break(None)
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
      case (block: AST.BlockExpression, i) if block.elements.nonEmpty && block.elements.last.isInstanceOf[AST.ClosureExpression] => i
    }.toSet
    if (untypedClosureIndices.nonEmpty) {
      resolveConstructorForClosures(node, typeRef, context, untypedClosureIndices) match {
        case Some(result) => break(result)
        case None => // ambiguous/unresolved: fall through to the eager path, which reports E0052 / constructor-not-found
      }
    }

    // Existing positional argument handling. A malleable argument (a
    // list/map literal, or a generic static/unqualified/new-object call --
    // see isMalleableArgument) can fail outright when typed with no expected
    // type, e.g. `new Leaf()` for a zero-field generic case with nothing of
    // its own to pin its type parameter from. That failure is fatal to
    // typedTerms (it returns null for the whole array on any single
    // argument failure), which previously skipped the expected-type retry
    // machinery below entirely (retypeConstructorArguments only recovers an
    // argument that typed to the *wrong* type, not one that failed to type
    // at all). Suppress reporting for the initial attempt when a malleable
    // argument is present, and fall back to resolveConstructorForMalleableArgs
    // -- which types the non-malleable arguments first, narrows to a unique
    // constructor candidate, and retypes the malleable ones against its
    // resolved parameter types -- before giving up (#1399).
    val malleableIndices = node.args.zipWithIndex.collect {
      case (expr, i) if isMalleableArgument(expr) && !untypedClosureIndices.contains(i) => i
    }.toSet
    val parameters0 =
      if (malleableIndices.nonEmpty) typing.withSuppressedReporting(typedTerms(node.args.toArray, context))
      else typedTerms(node.args.toArray, context)
    if (parameters0 == null) {
      if (malleableIndices.nonEmpty) {
        resolveConstructorForMalleableArgs(node, typeRef, context, malleableIndices) match {
          case Some(result) => break(result)
          case None => // unresolved: fall through and re-type without suppression to report the real diagnostic
        }
      }
      typedTerms(node.args.toArray, context)
      break(None)
    }

    // `a..b`/`a..<b` desugars to `new onion.Range(a, b, inclusive)` at parse
    // time (OnionParser.rangeNew / the JavaCC grammar's equivalent
    // production), so a nullable bound never dereferences anything directly
    // -- it just fails ordinary constructor-overload resolution below and
    // reports the generic constructor-not-found error, leaking the "Range"
    // implementation-detail class name the user never wrote. Special-case it
    // up front, mirroring typeNewArray's nullable-array-size check.
    if (typeRef.name == "onion.Range" && parameters0.length >= 2) {
      val nullableBoundIndex =
        if (parameters0(0).`type`.isInstanceOf[NullableType]) 0
        else if (parameters0(1).`type`.isInstanceOf[NullableType]) 1
        else -1
      if (nullableBoundIndex >= 0) {
        val nullable = parameters0(nullableBoundIndex).`type`.asInstanceOf[NullableType]
        bodyContext.report(NULLABLE_MEMBER_ACCESS, node.args(nullableBoundIndex), nullable.displayName, "rangeBound")
        break(None)
      }
    }

    // The exact match goes by erasure; drop a constructor whose parameter the argument's
    // own view contradicts, as a method call already does: `new D(b)` with `b: JInteger`
    // and `def this(c: Comparable[String])` compiled and threw (#2649).
    def ownViewAccepts(ctors: Array[TypedAST.ConstructorRef], params: Array[Term]): Array[TypedAST.ConstructorRef] =
      ctors.filter { c =>
        val formals = c.getArgs
        formals.length != params.length || formals.indices.forall { i =>
          params(i) == null || !TypeRelations.ownViewRejects(bodyContext.table, formals(i), params(i).`type`)
        }
      }
    // A slot typed by a class type variable (`List[T]` with `T` the enclosing class's own
    // variable) is matched through T's bound, so a `List[Number]` passes it. Check it by
    // the rule an assignment uses instead, as method calls do (#2676): the argument must
    // be that very variable's type. Primitives, null and empty collection literals are
    // converted or adopted later and are left alone (#2677).
    def typeVariableSlotsFit(c: TypedAST.ConstructorRef, params: Array[Term]): Boolean = typeRef match {
      case applied: TypedAST.AppliedClassType =>
        val classSubst = TypeSubstitution.classSubstitution(applied)
        val formals = c.getArgs.map(t =>
          TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
        formals.length != params.length || formals.indices.forall { i =>
          AssignabilitySupport.typeVariableSlotFits(formals(i), params(i), bodyContext.table)
        }
      case _ => true
    }
    // With overloads, prefer the ones that check accepts: Java picks `this(Object)` for a
    // `List[Number]` when the other overload takes `List[T]`.
    val constructors0 = ownViewAccepts(
      typeRef.findConstructor(parameters0, (c: TypedAST.ConstructorRef) => typeVariableSlotsFit(c, parameters0)),
      parameters0)
    // Exact matching is substitution-blind (an applied Pair[String, Integer]
    // still exposes (A, B)): retry against the substituted signatures with
    // boxing so 'new Pair[String, Integer]("x", 42)' boxes 42
    val constructors1 = if (constructors0.nonEmpty) constructors0 else findConstructorWithBoxing(typeRef, parameters0)
    // Guard the substitution-blind exact match: for an applied generic type the
    // matched constructor must accept the arguments under the type-argument
    // substitution (T -> String), not merely under the erased bound (Object).
    // Otherwise `new Box[String](aStringBuilder)` compiled and threw a runtime
    // ClassCastException — a type-safety hole the instance-method path does not have.
    def nonNullCore(t: TypedAST.Type): TypedAST.Type = t match {
      case n: TypedAST.NullableType => n.innerType
      case other => other
    }
    def substitutionValidFor(ctors: Array[TypedAST.ConstructorRef], params: Array[Term]): Boolean = typeRef match {
      case applied: TypedAST.AppliedClassType if ctors.length == 1 =>
        val classSubst = TypeSubstitution.classSubstitution(applied)
        val formals = ctors(0).getArgs.map(t =>
          TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
        // Only reject on an arity-matched signature; differing arity is handled
        // by the vararg/default-parameter paths below, so leave it to them.
        formals.length != params.length ||
          formals.indices.forall { i =>
            // A type-variable slot is abstract (#2332): only that variable, or
            // one whose bound chain reaches it, fits. Erasure would let an
            // unrelated `U` through, to fail later as a ClassCastException.
            formals(i) match {
              case tv: TypedAST.TypeVariableType if params(i).`type`.isInstanceOf[TypedAST.TypeVariableType] =>
                TypeRules.isSuperTypeByIdentity(tv, params(i).`type`)
              // A `T?` slot takes what a `T` slot takes, plus null: `new S[T, U](u, u)`
              // into `def this(x: T?, ...)` compiled and threw on the next read (#2332).
              case n: TypedAST.NullableType
                  if n.innerType.isInstanceOf[TypedAST.TypeVariableType] &&
                    nonNullCore(params(i).`type`).isInstanceOf[TypedAST.TypeVariableType] =>
                TypeRules.isSuperTypeByIdentity(n.innerType, nonNullCore(params(i).`type`))
              case f =>TypeRelations.isConstructorArgAssignable(f, params(i).`type`, bodyContext.table)
            }
          } && typeVariableSlotsFit(ctors(0), params)
      case _ => true
    }
    // Nothing matched eagerly, or it only matched via the empty-collection-literal
    // loophole (StandardParameterMatcher/emptyCollectionLiteralAccepts accepts `[]`/
    // `[:]` against ANY parameterization of the same raw collection -- including one
    // whose type argument comes solely from the class's own unresolved type
    // parameter, e.g. `entries: List[K]` with no other argument pinning K): a
    // malleable argument (a list/map literal, or a generic static/unqualified/
    // new-object call) may have been typed with no expected type before the
    // constructor was known, defaulting an unconstrained type argument to Object --
    // e.g. `new Simple[Int](1, [])` types `[]` as List[Object], which is not
    // assignable to the formal List[T]=List[Int]. Retype those arguments against a
    // uniquely arity-matched constructor's substituted formal types and retry,
    // mirroring the fallback ordinary method calls already get from
    // ArgumentExpectedTypeRetyping (#232).
    val retyped =
      if (constructors1.isEmpty || !substitutionValidFor(constructors1, parameters0))
        retypeConstructorArguments(typeRef, node.args.toArray, parameters0, context)
      else None
    val parameters = retyped.getOrElse(parameters0)
    val constructors = retyped match {
      case Some(newParams) =>
        val exact = ownViewAccepts(
          typeRef.findConstructor(newParams, (c: TypedAST.ConstructorRef) => typeVariableSlotsFit(c, newParams)),
          newParams)
        if (exact.nonEmpty) exact else findConstructorWithBoxing(typeRef, newParams)
      case None => constructors1
    }
    if (!substitutionValidFor(constructors, parameters)) {
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
      if (defaultsCandidate) break(typeNewObjectWithNamedArgs(node, typeRef, context, parameters0))
      // Varargs fallback, as for methods: `new ProcessBuilder("echo", "x")` spreads its
      // trailing arguments into the `String...` array (#2839). It comes after the
      // default-parameter fallback, as method resolution orders them.
      varargConstruction(node, typeRef, parameters) match {
        case Some(result) => break(result)
        case None =>
      }
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
    } else if (!constructorAccessible(node, constructors(0))) {
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
          warnNullArguments(constructors(0), node.args.toArray, parameters, formals)
          Some(new NewObject(appliedCtor, adaptToFormals(parameters, formals)))
        case _ =>
          warnNullArguments(constructors(0), node.args.toArray, parameters, constructors(0).getArgs)
          Some(new NewObject(constructors(0), adaptToFormals(parameters, constructors(0).getArgs)))
      }
    }
  }

  /**
   * Resolves `new C(a, b, c)` against a varargs constructor `C(x: X, ys: Y...)` when no
   * constructor takes the arguments as written: the fixed arguments must fit their slots
   * and every trailing one the array's element type, as for a varargs method (#2839).
   * Returns None when no varargs constructor fits, so the caller reports E0021;
   * Some(None) when it reported an error itself; Some(Some(term)) on success.
   */
  private def varargConstruction(node: AST.NewObject, typeRef: ClassType, parameters: Array[Term]): Option[Option[Term]] = {
    val table = bodyContext.table
    def formalsOf(c: ConstructorRef): Array[Type] = typeRef match {
      case applied: TypedAST.AppliedClassType =>
        val classSubst = TypeSubstitution.classSubstitution(applied)
        c.getArgs.map(t =>
          TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
      case _ => c.getArgs
    }
    val candidates = typeRef.constructors.toSeq.filter(_.isVararg).flatMap { c =>
      val formals = formalsOf(c)
      val fixed = formals.length - 1
      formals.lastOption match {
        case Some(arrayType: ArrayType) if parameters.length >= fixed =>
          // A slot typed by a type variable is abstract: only that variable fits it (#2332),
          // as on the exact-arity path, so `T...` never takes an `Object` (#2839).
          val fixedFit = (0 until fixed).forall(i =>
            TypeRelations.isAssignableWithBoxing(formals(i), parameters(i).`type`, table) &&
              AssignabilitySupport.typeVariableSlotFits(formals(i), parameters(i), table))
          val restFit = parameters.drop(fixed).forall(p =>
            TypeRelations.isAssignableWithBoxing(arrayType.base, p.`type`, table) &&
              AssignabilitySupport.typeVariableSlotFits(arrayType.base, p, table))
          if (fixedFit && restFit) Some((c, formals, arrayType)) else None
        case _ => None
      }
    }
    candidates match {
      case Seq() => None
      case Seq((ctor, formals, arrayType)) =>
        if (!constructorAccessible(node, ctor)) return Some(None)
        val fixed = formals.length - 1
        val fixedArgs = adaptToFormals(parameters.take(fixed), formals.take(fixed))
        val rest = parameters.drop(fixed)
        // A boxed element for a primitive array (`Integer` into `Int...`) is unboxed
        // first, as the varargs method path does; stored as-is it failed verification.
        val elements = rest.map { p =>
          val unboxed = arrayType.base match {
            case _: BasicType if !p.isBasicType =>
              Boxing.unboxedType(table, p.`type`).map(Boxing.unboxing(table, p, _)).getOrElse(p)
            case _ => p
          }
          Boxing.adaptArgument(table, unboxed, arrayType.base)
        }
        val args = fixedArgs :+ (new NewArrayWithValues(arrayType, elements): Term)
        val ref = typeRef match {
          case applied: TypedAST.AppliedClassType =>
            new TypedAST.ConstructorRef {
              def modifier: Int = ctor.modifier
              def affiliation: TypedAST.ClassType = applied
              def name: String = ctor.name
              def getArgs: Array[TypedAST.Type] = ctor.getArgs
              override def isVararg: Boolean = true
            }
          case _ => ctor
        }
        Some(Some(new NewObject(ref, args)))
      case Seq((c1, _, _), (c2, _, _), _*) =>
        bodyContext.report(
          AMBIGUOUS_CONSTRUCTOR,
          node,
          Array[AnyRef](c1.affiliation, c1.getArgs),
          Array[AnyRef](c2.affiliation, c2.getArgs)
        )
        Some(None)
    }
  }

  /**
   * Reports E0013 when the resolved constructor is not accessible from the
   * current class. Every `new` resolution path (exact, varargs, defaults,
   * named arguments, lambda and malleable arguments) checks this before it
   * builds a NewObject; the paths that skipped it compiled a private
   * constructor call that threw IllegalAccessError at run time (#2827).
   */
  private def constructorAccessible(node: AST.NewObject, ctor: ConstructorRef): Boolean =
    MemberAccess.isMemberAccessible(ctor, bodyContext.definition) || {
      bodyContext.report(METHOD_NOT_ACCESSIBLE, node, ctor.affiliation, "new", ctor.getArgs, bodyContext.definition)
      false
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
    Boxing.adaptArguments(bodyContext.table, parameters, formals)

  /** W0012 for a `null` argument to an Onion-declared constructor's non-null
   *  parameter, as a method call already warns (#2464). A Java constructor's
   *  parameter nullness is unknown, so it stays silent (#2116). */
  private def warnNullArguments(ctor: ConstructorRef, args: Array[? <: AST.Node], params: Array[Term], formals: Array[Type]): Unit =
    if (ctor.isInstanceOf[ConstructorDefinition]) {
      var i = 0
      while (i < params.length && i < args.length && i < formals.length) {
        val p = params(i)
        val f = formals(i)
        if (p != null && args(i) != null && p.`type`.isNullType && !f.isBasicType && !f.isNullable && !f.isNullType &&
            !TypeRelations.isBareNullableTypeVariable(f)) {
          bodyContext.warningReporter.setSourceFile(bodyContext.sourceFile)
          bodyContext.warningReporter.nullToNonNullable(args(i).location, f.displayName)
        }
        i += 1
      }
    }

  /** Whether `actual` can fill a parameter declared `formal`, either through
    * ordinary boxing-aware assignability or -- for a literal like `-3` against
    * a `Byte`/`Short`/`Char` formal -- constant narrowing (issue #374). */
  private def formalAccepts(formal: Type, actual: Term): Boolean =
    TypeRelations.isConstructorArgAssignable(formal, actual.`type`, bodyContext.table) ||
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
   * When no constructor matched the eagerly-typed arguments, retype malleable
   * arguments (list/map literals, and generic static/unqualified/new-object
   * calls) against a uniquely arity-matched constructor's substituted formal
   * types, and return the updated arguments if at least one changed type.
   * Returns None when there is no single arity-matched candidate or nothing
   * changed, so the caller falls back to the original not-found diagnostic.
   */
  private def retypeConstructorArguments(
    typeRef: ClassType,
    args: Array[AST.Expression],
    params: Array[Term],
    context: LocalContext
  ): Option[Array[Term]] = {
    if (params == null || params.isEmpty) return None
    val malleableIndices = args.indices.filter(i => i < params.length && isMalleableArgument(args(i)))
    if (malleableIndices.isEmpty) return None

    val candidates = typeRef.constructors.filter(_.getArgs.length == params.length)
    if (candidates.length != 1) return None

    val classSubst = TypeSubstitution.classSubstitution(typeRef)
    val formals = candidates(0).getArgs.map(t =>
      TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))

    var changed = false
    val updated = params.clone()
    for (i <- malleableIndices) {
      val expectedType = formals(i)
      if (expectedType != null) {
        typing.withSuppressedReporting(typed(args(i), context, expectedType)) match {
          case Some(term) if term.`type` != null && !(term.`type` eq params(i).`type`) =>
            updated(i) = term
            changed = true
          case _ =>
        }
      }
    }
    if (changed) Some(updated) else None
  }

  /** An argument whose inferred type can shift under an expected type: a
    * generic static/unqualified/new-object call, or a collection literal
    * (whose element/entry types are target-typed). Mirrors
    * [[ArgumentExpectedTypeRetyping.isMalleable]] for constructor arguments. */
  private def isMalleableArgument(expr: AST.Expression): Boolean = expr match {
    case _: AST.StaticMethodCall => true
    case _: AST.UnqualifiedMethodCall => true
    case _: AST.NewObject => true
    case _: AST.ListLiteral => true
    case _: AST.MapLiteral => true
    case _ => false
  }

  /**
   * Resolve a constructor when one or more arguments are malleable (see
   * isMalleableArgument) and typing them with no expected type fails
   * outright -- e.g. `new Leaf()` for a zero-field generic case with
   * nothing of its own to pin its type parameter from. Types the
   * non-malleable arguments first, narrows to a uniquely arity- and
   * type-matched constructor candidate from those, then retypes the
   * malleable arguments against that candidate's resolved parameter types.
   * Returns None (to fall back to the eager path, which reports the
   * original diagnostic) when a non-malleable argument itself fails to
   * type, no candidate uniquely matches, or a malleable argument still
   * fails to type against the resolved expected type. Mirrors
   * resolveConstructorForClosures for a malleable, non-closure argument.
   * Some(None) means the one match is not accessible (E0013 is reported).
   */
  private def resolveConstructorForMalleableArgs(
    node: AST.NewObject,
    typeRef: ClassType,
    context: LocalContext,
    malleableIndices: Set[Int]
  ): Option[Option[Term]] = boundary {
    val args = node.args.toArray
    val classSubst = TypeSubstitution.classSubstitution(typeRef)
    def substitutedArgs(c: ConstructorRef): Array[Type] =
      c.getArgs.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))

    val prelim = new Array[Term](args.length)
    for (i <- args.indices if !malleableIndices.contains(i)) {
      typing.withSuppressedReporting(typed(args(i), context)) match {
        case Some(t) => prelim(i) = t
        case None => break(None)
      }
    }

    val candidates = typeRef.constructors.filter { c =>
      val formals = substitutedArgs(c)
      formals.length == args.length &&
        args.indices.forall { i =>
          malleableIndices.contains(i) || TypeRelations.isConstructorArgAssignable(formals(i), prelim(i).`type`, bodyContext.table)
        }
    }
    if (candidates.length != 1) break(None)

    val ctor = candidates(0)
    if (!constructorAccessible(node, ctor)) break(Some(None))
    val formals = substitutedArgs(ctor)
    val finalParams = new Array[Term](args.length)
    for (i <- args.indices) {
      if (malleableIndices.contains(i)) {
        typing.withSuppressedReporting(typed(args(i), context, formals(i))) match {
          case Some(t) => finalParams(i) = t
          case None => break(None)
        }
      } else {
        val p = prelim(i)
        finalParams(i) = Boxing.adaptArgument(bodyContext.table, p, formals(i))
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
        Some(Some(new NewObject(appliedCtor, finalParams)))
      case _ =>
        Some(Some(new NewObject(ctor, finalParams)))
    }
  }

  /**
   * Resolve a constructor when some arguments are lambdas with untyped
   * parameters: type the non-closure arguments, pick the unique constructor
   * that matches on arity and those arguments, then type each closure against
   * the constructor's corresponding parameter type. Returns None (to fall back
   * to eager handling) when resolution is ambiguous or finds no single match,
   * and Some(None) when the one match is not accessible (E0013 is reported).
   */
  /** Whether a lambda with `arity` parameters can be converted to `formal` --
    * i.e. `formal` is an onion.FunctionN type or a Java functional interface
    * (a single abstract non-Object method) whose SAM takes `arity` arguments.
    * Used to disambiguate constructor overloads at a closure argument position
    * (e.g. Thread(Runnable) vs Thread(String) for `new Thread(() -> ...)`). */
  private def closureCanTarget(formal: Type, arity: Int): Boolean = formal match {
    case target: ClassType =>
      val name = (target match { case a: AppliedClassType => a.raw; case _ => target }).name
      val ct = FunctionalInterfaces.functionalView(target)
      val raw = ct match { case a: AppliedClassType => a.raw; case _ => ct }
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
  ): Option[Option[Term]] = boundary {
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
          if (closureIndices.contains(i)) {
            // The parser may wrap `{ param -> body }` in a BlockExpression whose
            // sole element is the ClosureExpression — extract whichever we get.
            val arity = args(i) match {
              case c: AST.ClosureExpression => c.args.length
              case block: AST.BlockExpression if block.elements.nonEmpty =>
                block.elements.last match {
                  case c: AST.ClosureExpression => c.args.length
                  case _ => -1
                }
              case _ => -1
            }
            closureCanTarget(formals(i), arity)
          } else
            TypeRelations.isConstructorArgAssignable(formals(i), prelim(i).`type`, bodyContext.table)
        }
    }
    if (candidates.length != 1) break(None)

    val ctor = candidates(0)
    if (!constructorAccessible(node, ctor)) break(Some(None))
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
        finalParams(i) = Boxing.adaptArgument(bodyContext.table, p, formals(i))
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
        Some(Some(new NewObject(appliedCtor, finalParams)))
      case _ =>
        Some(Some(new NewObject(ctor, finalParams)))
    }
  }

  /**
   * Handle constructor call with named arguments
   */
  private def typeNewObjectWithNamedArgs(
    node: AST.NewObject,
    typeRef: ClassType,
    context: LocalContext,
    positional: Array[Term] = null
  ): Option[Term] =
    resolveConstructorWithNamedArgs(node, node.args, typeRef, context, positional)
      .filter { case (ctor, _, _) => constructorAccessible(node, ctor) }
      .map { case (ctor, resolved, _) =>
        val params = ArgumentHelpers.evaluateInWrittenOrder(node.args, ctor.argumentsWithDefaults.toSeq.map(_.name), resolved, context)
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

  /**
   * Resolves a constructor of `typeRef` for an argument list with named
   * arguments, binding each by name and filling omitted ones from their
   * defaults. Shared by `new A(b = 1)` and the delegations `extends A(b = 1)`
   * and `: this(b = 1)`, which used to bind them by position (#2659).
   * Returns the constructor, its full argument list, and the source node of
   * each argument (null where a default was filled in).
   *
   * `positional`, when given, holds the already-typed arguments of an
   * all-positional call. Two constructors that both take that many arguments
   * once their defaults are filled are then told apart by type, as a method
   * call is: `new A(7)` picks `A(a: Int, b: Int = 2)` over `A(s: String)`
   * (#2660).
   */
  def resolveConstructorWithNamedArgs(
    node: AST.Node,
    args: List[AST.Expression],
    typeRef: ClassType,
    context: LocalContext,
    positional: Array[Term] = null
  ): Option[(ConstructorDefinition, Array[Term], Array[AST.Node])] = {
    // Extract named argument info
    val namedInfo = extractNamedArgInfo(args)

    // Filter constructors by named argument compatibility
    val byCount = filterConstructorsByNamedArgs(typeRef.constructors.toIndexedSeq, namedInfo)
    val candidates =
      if (byCount.length <= 1 || positional == null || positional.contains(null) ||
          namedInfo._2.nonEmpty || positional.length != namedInfo._1) byCount
      else {
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        val byType = byCount.filter {
          case cd: ConstructorDefinition =>
            positional.indices.forall { i =>
              val formal = TypeSubstitution.substituteType(cd.argumentsWithDefaults(i).argType, classSubst,
                scala.collection.immutable.Map.empty, defaultToBound = false)
              formalAccepts(formal, positional(i))
            }
          case _ => false
        }
        // No candidate takes the arguments: `extends A(true)` is "not found",
        // not "ambiguous between A(Int, Int) and A(String)".
        if (byType.isEmpty) {
          bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(positional), typeRef.constructors)
          return None
        }
        byType
      }

    if (candidates.isEmpty) {
      val unknownNamed = typeRef.constructors.collectFirst { case cd: ConstructorDefinition => cd } match {
        case Some(_) =>
          val paramNameSets = typeRef.constructors.collect {
            case cd: ConstructorDefinition => cd.argumentsWithDefaults.map(_.name).toSet
          }
          args.collectFirst {
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
          val parameters = typedTerms(args.toArray.filterNot(_.isInstanceOf[AST.NamedArgument]), context)
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
    val sources = new Array[AST.Node](ctor.argumentsWithDefaults.length)
    processNamedArgsForConstructor(node, args, typeRef, ctor, context, sources).map(params => (ctor, params, sources))
  }

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
   * Process named arguments: reorder and fill defaults.
   *
   * Every argument is typed against its (possibly class-type-substituted)
   * formal type, mirroring the positional path in `typeNewObject`: this
   * steers inference for a malleable argument (an empty list/map literal, a
   * generic call), but `typed(..., expected)` still returns a term of the
   * argument's own actual type on a mismatch rather than failing outright,
   * and `filterConstructorsByNamedArgs` above only checked argument count and
   * parameter names, never types. So an incompatible argument (e.g. a String
   * literal supplied by name for an Int-bound type parameter) used to compile
   * successfully and throw a ClassCastException at run time instead of being
   * rejected here, mirroring what `substitutionValidFor` already guards on
   * the positional path. The final `adaptToFormals` call is likewise needed
   * for the same reason it is on the positional path: an argument needing
   * ordinary boxing or numeric widening (e.g. an `Int` literal for a `Double`
   * formal) left unconverted corrupts the JVM stack map frames and crashes
   * `BytecodeGeneration` with an internal `I0000` error instead of compiling.
   */
  private def processNamedArgsForConstructor(
    node: AST.Node,
    args: List[AST.Expression],
    typeRef: ClassType,
    ctor: ConstructorDefinition,
    context: LocalContext,
    sources: Array[AST.Node]
  ): Option[Array[Term]] = {
    val argsWithDefaults = ctor.argumentsWithDefaults
    val paramNames = argsWithDefaults.map(_.name)
    val classSubst = TypeSubstitution.classSubstitution(typeRef)
    val formals = argsWithDefaults.map(a =>
      TypeSubstitution.substituteType(a.argType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    val result = new Array[Term](argsWithDefaults.length)
    val filled = new Array[Boolean](argsWithDefaults.length)
    // The supplied arguments in written order, so E0021 names the call's own
    // types (`A(String)`), not the constructor's formals, which the
    // "available constructors" list beneath it already shows.
    val written = scala.collection.mutable.ArrayBuffer[Term]()
    def suppliedTypes: Array[Type] = written.map(_.`type`).toArray

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
            // Type the value against its formal type
            typed(named.value, context, formals(paramIndex)) match {
              case Some(term) =>
                written += term
                result(paramIndex) = term
                filled(paramIndex) = true
                sources(paramIndex) = named.value
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
            typed(expr, context, formals(positionalIndex)) match {
              case Some(term) =>
                written += term
                result(positionalIndex) = term
                filled(positionalIndex) = true
                sources(positionalIndex) = expr
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
        suppliedTypes, ctor.affiliation.constructors)
      return None
    }

    // Reject a supplied argument whose actual type doesn't fit its formal
    // type -- targeting the expected type above only steers inference, it
    // does not itself enforce assignability.
    val suppliedIndices = argsWithDefaults.indices.filter(filled)
    // A slot typed by a class type variable takes the assignment rule, as on the
    // positional path: `new C[T](xs = xs)` with `xs: List[Number]` is not a `List[T]`.
    if (!suppliedIndices.forall(i =>
        formalAccepts(formals(i), result(i)) &&
          AssignabilitySupport.typeVariableSlotFits(formals(i), result(i), bodyContext.table))) {
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, ctor.affiliation,
        suppliedTypes, ctor.affiliation.constructors)
      return None
    }
    warnNullArguments(ctor, sources, result, formals)

    // Fill missing arguments with default values
    argsWithDefaults.indices.foreach { i =>
      if (!filled(i)) {
        result(i) = argsWithDefaults(i).defaultValue.getOrElse {
          throw new IllegalStateException(s"constructor argument ${argsWithDefaults(i).name} has no default despite the required-argument check")
        }
        filled(i) = true
      }
    }

    Some(adaptToFormals(result, formals))
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
