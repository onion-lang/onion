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

  def mapFromExtensionReceiver(typeNode: AST.TypeNode): Option[Type] =
    mapFromExtensionReceiver(typeNode, typing.mapper_)

  /**
   * Resolves an `extension` block's receiver type, same as `mapFrom`, but with a
   * dedicated hint for the common mistake of writing `extension List[T] { ... }`
   * as if `T` were a fresh type parameter (mirroring `class Box[T]`/`record R[T]`
   * syntax). `extension_decl` (grammar/JJOnionParser.jj) has no leading `[T]`
   * parameter list of its own, so `T` resolves as an ordinary (here,
   * unresolvable) type reference, and the receiver as a whole then fails with
   * only the generic "type List[T] is not found. Check spelling or add an
   * import." -- actively misleading, since `List` plainly exists. When the
   * receiver is exactly that shape (a parameterized type over an otherwise-known
   * generic class, with an unresolvable bare type argument), reports
   * CLASS_NOT_FOUND with an extra hint naming the two real options: a raw
   * `extension List { ... }` (every instantiation, erasure-based) or a concrete
   * `extension List[Int] { ... }` (one specific instantiation).
   *
   * Shared by `TypingOutlinePass` and `DeclarationBodySupport`, which both
   * independently resolve the same receiver type, so both reports carry the hint.
   */
  def mapFromExtensionReceiver(typeNode: AST.TypeNode, mapper: NameResolver): Option[Type] = {
    val looksLikeFreshTypeParam = typeNode.desc match {
      case AST.ParameterizedType(base, params) =>
        val hasUnresolvedBareArg = params.exists {
          case ref: AST.ReferenceType if !ref.qualified => mapper.map(ref) == null
          case _ => false
        }
        hasUnresolvedBareArg && (mapper.map(base) match {
          case ct: ClassType => ct.typeParameters.nonEmpty
          case _ => false
        })
      case _ => false
    }
    if (!looksLikeFreshTypeParam) {
      mapFrom(typeNode, mapper)
    } else {
      val mappedType = mapper.resolveNode(typeNode)
      if (mappedType != null) Some(mappedType)
      else {
        val baseSimpleName = typeNode.desc match {
          case AST.ParameterizedType(AST.ReferenceType(name, _), _) => name
          case _ => typeNode.desc.toString
        }
        typing.report(
          CLASS_NOT_FOUND,
          typeNode,
          typeNode.desc.toString,
          mapper.getCandidateClassNames(typeNode.desc),
          "extensionTypeParam",
          baseSimpleName
        )
        None
      }
    }
  }

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
      typing.report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames(typeNode.desc))
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

  inline def openTypeParams[A](scope: TypeParamScope)(inline block: A): A = {
    val prev = typing.typeParams_
    // Most openings add nothing (a method without type parameters inside a class without
    // them); `++` then returns the same scope, and there is nothing to switch.
    if (scope eq prev) block
    else {
      typing.setTypeParams(scope)
      try block
      finally typing.setTypeParams(prev)
    }
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
    // Phase 2: resolve each real bound, one parameter at a time, growing the
    // scope as we go. A bound referencing an earlier parameter in this same
    // list (`[B extends CharSequence, T extends B]`) must see that
    // parameter's just-resolved real bound, not its Phase 1 placeholder
    // (whose bound is always the root class) -- otherwise `T` silently sees
    // `B` as bounded by the root class instead of `CharSequence`, both in
    // member lookup (`t.length()`) and in the Liskov checks that walk a type
    // variable's bound chain. A later parameter still isn't visible to an
    // earlier one's bound (ordinary declaration-order scoping); self- and
    // mutually-F-bounded references still resolve via their Phase 1
    // placeholder, exactly as before.
    // A bound that is a bare later parameter (`[A extends B, B]`) is resolved
    // after that parameter, so `A`'s bound is `B` itself, not `B`'s
    // placeholder. Through the placeholder, `A`'s bound chain never reached
    // `B`: joining an `A` with a `B` failed with "type B is expected, but
    // type B is used" (#2308). A cycle keeps declaration order.
    val byName = distinct.map(tp => tp.name -> tp).toMap
    def bareBoundParam(tp: AST.TypeParameter): Option[AST.TypeParameter] =
      tp.upperBound.map(_.desc).collect {
        case AST.ReferenceType(n, false) => n
        case AST.NullableType(AST.ReferenceType(n, false)) => n
      }.filter(_ != tp.name).flatMap(byName.get)
    val resolutionOrder = Buffer[AST.TypeParameter]()
    val visiting = MutableSet[String]()
    def visit(tp: AST.TypeParameter): Unit =
      if (!visiting.contains(tp.name)) {
        visiting += tp.name
        bareBoundParam(tp).foreach(visit)
        resolutionOrder += tp
      }
    distinct.foreach(visit)
    val outerScope = typing.typeParams_
    var scope = outerScope ++ placeholders
    typing.setTypeParams(scope)
    try {
      val resolvedByName = resolutionOrder.map { tp =>
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
        val resolved = TypeParam(tp.name, variableType, upper, constraintTypes)
        scope = scope ++ Seq(resolved)
        typing.setTypeParams(scope)
        tp.name -> resolved
      }.toMap
      // A bound that names its own parameter (`A` in `[A extends Animal[A]]`)
      // still holds the placeholder, so a member reached through that bound
      // (`me().me()`) yields the placeholder, not `A`. Link it, so a `T` slot
      // recognises it as the same parameter (#2308).
      placeholders.foreach(ph => ph.variableType.resolvedParameter = resolvedByName(ph.name).variableType)
      distinct.map(tp => resolvedByName(tp.name)).toSeq
    } finally {
      typing.setTypeParams(outerScope)
    }
  }

  private def validateTypeApplication(typeNode: AST.TypeNode, mappedType: Type): Unit = {
    // `Box[String, String]?` arrives as NullableType(ParameterizedType(..)) and used to
    // fall through both matches below, so a wrong arity in a nullable annotation was
    // accepted silently while the same arity failed on `new` (issue #413). Validate
    // through the nullable wrapper.
    val desc = typeNode.desc match {
      case AST.NullableType(inner) => inner
      case other                   => other
    }
    val mapped = mappedType match {
      case n: TypedAST.NullableType => n.innerType
      case other                    => other
    }
    desc match {
      case AST.ParameterizedType(_, _) | AST.FunctionType(_, _) =>
        mapped match {
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
            // The per-argument bound check walks the argument's supertype chain,
            // which is not yet established for a self/forward reference like
            // `class Sub : Base[Sub]` (Base[T extends Base[T]]) at the moment its
            // own supertypes are resolved. When bound checks are deferred (during
            // hierarchy construction), queue this application's bound check to run
            // once the chain is in place; otherwise run it now. Genuine violations
            // (`Base[String]`) still fail — the deferred check reports on flush.
            if (typing.boundChecksDeferred) {
              typing.deferBoundCheck(() => checkTypeArgumentBounds(typeNode, applied, rawParams))
            } else {
              checkTypeArgumentBounds(typeNode, applied, rawParams)
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def checkTypeArgumentBounds(
    typeNode: AST.TypeNode,
    applied: TypedAST.AppliedClassType,
    rawParams: Array[TypedAST.TypeParameter]
  ): Unit = {
            // A later parameter's declared bound can reference an earlier one
            // (`[A, B extends A, T extends B]`): substitute THIS instantiation's
            // own type arguments into each bound before checking it, so `T`'s
            // bound reads as the real argument given for `B` (e.g. `String`),
            // not the abstract, unsubstituted type-variable object named `B`.
            // Without this, `C3[Object, String, String]` checked `String`
            // against literal `B` and failed, even though `B` substitutes to
            // exactly `String` at this instantiation (#2259 review repro).
            val fullSubst: scala.collection.immutable.Map[String, TypedAST.Type] =
              rawParams.indices.map(j => rawParams(j).name -> applied.typeArguments(j)).toMap
            rawParams.indices.foreach { i =>
              val param = rawParams(i)
              val declaredUpper = param.upperBound.getOrElse(typing.rootClass)
              // Exclude this parameter's OWN name from the substitution: an
              // F-bounded self-reference (`T extends Comparable[T]`) is its
              // own argument here (`Tree[T]` used inside `Tree`'s own body),
              // and substituting it would swap the bound's internal `T`
              // (frozen, by construction, to the Phase-1 placeholder used
              // while every bound in the declaration was first resolved)
              // for this argument's `T` (the class's real, final type
              // variable) -- the two are different objects, so the swap
              // made a self-bound compare against itself inconsistently
              // and broke `class Tree[T extends Comparable[T]]` (caught by
              // review). Any OTHER parameter's name still substitutes, so
              // a genuine cross-reference to an earlier parameter is
              // unaffected.
              val selfSubst = fullSubst - param.name
              val upper = TypeSubstitution.substituteType(declaredUpper, selfSubst, Map.empty, defaultToBound = false) match {
                case ct: TypedAST.ClassType => ct
                case _ => declaredUpper
              }
              val arg = applied.typeArguments(i)
              if (arg eq BasicType.VOID) {
                typing.report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeNode, arg.name)
              } else {
                arg match {
                  case wildcard: TypedAST.WildcardType =>
                    val wildcardUpper = wildcard.upperBound
                    if (!TypeRules.isAssignable(upper, wildcardUpper)) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, wildcardUpper)
                    }
                  // Nullability is checked apart from assignability: the
                  // Object top-type rule accepts T?, so a non-null Object
                  // bound can't reject String? through isAssignable alone
                  case n: TypedAST.NullableType =>
                    if (param.nullability == Nullability.NonNull) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                    } else if (!TypeRules.isAssignable(upper, typing.boxedTypeArgument(n.innerType))) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                    }
                  case _ =>
                    val checkedArg = typing.boxedTypeArgument(arg)
                    // Object is the top type, so any reference-type argument satisfies
                    // an unbounded (Object) parameter. Short-circuit here: isAssignable
                    // walks the argument's supertype chain, which is not yet established
                    // for a self/forward reference like `class Ver conforms Comparable[Ver]`
                    // during the outline pass, and would wrongly reject it.
                    val satisfiesObjectBound = (upper eq typing.rootClass) && !checkedArg.isBasicType
                    // `upper` can itself still be an unresolved type variable
                    // after substitution -- an enclosing, not-yet-instantiated
                    // parameter (`B` substitutes to `P` in `new Holder[P, Q](q)`
                    // inside `class Mk[P, Q]`). The ordinary isAssignable falls
                    // back to comparing erased classes there (both `P` and `Q`
                    // widen to Object), wrongly accepting two unrelated
                    // variables (#2259 review repro), so an abstract `upper`
                    // uses only identity through the argument's own bound
                    // chain -- never the lenient fallback -- to decide.
                    val satisfied = upper match {
                      case tv: TypedAST.TypeVariableType => TypeRules.isSuperTypeByIdentity(tv, checkedArg)
                      case _ => satisfiesObjectBound || TypeRules.isAssignable(upper, checkedArg)
                    }
                    if (!satisfied) {
                      typing.report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                    }
                }
              }
            }
  }
}
