package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.generics.Erasure
import onion.compiler.toolbox.Boxing

import scala.collection.mutable

private[compiler] object DuplicationChecks {
  private val emptyMethodSubst: scala.collection.immutable.Map[String, Type] =
    scala.collection.immutable.Map.empty

  private def erasedMethodDesc(method: Method): String =
    Erasure.methodDescriptor(method.returnType, method.arguments)

  private def erasedParamDescriptor(args: Array[Type]): String =
    args.map(Erasure.asmType).map(_.getDescriptor).mkString("(", "", ")")

  private def parameterPattern(args: Array[Type], typing: Typing): ErasedParameterPattern = {
    // Keep alternatives independent: materializing all complete signatures
    // would require 2^arity strings for primitive/boxed parameters.
    val perArg = args.map { arg =>
      val baseDesc = Erasure.asmType(arg).getDescriptor
      arg match
        case bt: BasicType if bt != BasicType.VOID =>
          Set(baseDesc, Erasure.asmType(typing.boxedTypeArgument(bt)).getDescriptor)
        case ct: ClassType =>
          val base = Set(baseDesc)
          Boxing.unboxedType(typing.table_, ct) match
            case Some(bt) => base + Erasure.asmType(bt).getDescriptor
            case None => base
        case _ =>
          Set(baseDesc)
    }
    ErasedParameterPattern(perArg)
  }

  private def primitiveAwareSuperType(implArg: Type, contractArg: Type, typing: Typing): Boolean = {
    if TypeRules.isSuperType(implArg, contractArg) then return true
    (implArg, contractArg) match
      case (bt: BasicType, ct: ClassType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case (ct: ClassType, bt: BasicType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case _ => false
  }

  private def primitiveAwareAssignable(contractType: Type, implType: Type, typing: Typing): Boolean = {
    if TypeRules.isAssignable(contractType, implType) then return true
    (contractType, implType) match
      case (bt: BasicType, ct: ClassType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case (ct: ClassType, bt: BasicType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case _ => false
  }

  def checkOverrideContracts(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    if clazz.isInterface then return
    val allViews = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz))
    // Exclude the target class itself to avoid checking methods against themselves
    val views = allViews - clazz
    if views.isEmpty then return

    val implementations = clazz.methods.filter(m => !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier))
    if implementations.isEmpty then return
    // Most methods do not override anything. Index by name first and only erase
    // a name's implementations when an inherited contract actually needs them.
    // Each per-name index retains the old last-implementation-wins behavior.
    val implByName = implementations.groupBy(_.name)
    val erasedByName = mutable.HashMap.empty[String, scala.collection.immutable.Map[String, Method]]

    for (view <- views.values) {
      val viewSubst: scala.collection.immutable.Map[String, Type] =
        view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap

      for (contract <- view.raw.methods) {
        if implByName.contains(contract.name) && !Modifier.isStatic(contract.modifier) && !Modifier.isPrivate(contract.modifier) then
          val implByErasedParams = erasedByName.getOrElseUpdate(
            contract.name,
            implByName(contract.name).iterator
              .map(m => (typing.table_.erasedParamsOf(m)(erasedParamDescriptor(m.arguments)), m))
              .toMap
          )
          val specializedArgs =
            contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
          // An implementation may declare the specialized parameter types
          // (id(x: String) for Id[String]) or the erased ones (id(x: Object)):
          // look the contract up under both keys.
          val specializedKey = erasedParamDescriptor(specializedArgs)
          val erasedKey = typing.table_.erasedParamsOf(contract)(erasedParamDescriptor(contract.arguments))
          implByErasedParams.get(specializedKey).orElse(implByErasedParams.get(erasedKey)).foreach { impl =>
            val specializedRet =
              TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)

            val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)

            // Check if trying to override a final method
            if Modifier.isFinal(contract.modifier) then
              val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
              typing.report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, view.raw.name)

            specializedArgs.zip(impl.arguments).foreach { case (arg, implArg) =>
              if (!primitiveAwareSuperType(implArg, arg, typing)) {
                typing.report(SemanticError.INCOMPATIBLE_TYPE, location, arg, implArg)
              }
            }

            if (!primitiveAwareAssignable(specializedRet, impl.returnType, typing)) {
              typing.report(SemanticError.INCOMPATIBLE_TYPE, location, specializedRet, impl.returnType)
            }
          }
      }
    }
  }

  /**
   * Verify that every method marked `override` actually overrides (or implements)
   * a method of the same name and an override-compatible signature declared by a
   * base class or an implemented interface. If nothing is overridden, report
   * OVERRIDE_TARGET_NOT_FOUND.
   *
   * Conservative by design: an implementation counts as overriding a base method
   * when they share the same name and the same erased parameter descriptor
   * (boxing-aware, matching the abstract-method-implementation check). Only when
   * NO base method matches by name+arity+param-types is the override rejected.
   */
  def checkOverrideTargets(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    if clazz.isInterface then return

    // Collect override-marked, non-static, non-private instance methods declared here.
    val overrideMethods =
      clazz.methods.filter(m =>
        AST.hasModifier(m.modifier, AST.M_OVERRIDE) &&
          !Modifier.isStatic(m.modifier) &&
          !Modifier.isPrivate(m.modifier))
    if overrideMethods.isEmpty then return

    val allViews = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz))
    // Exclude the target class itself: an override must target a *base* member.
    val views = allViews - clazz

    // Index actual base methods by name and arity, keeping per-position choices.
    // Include both the specialized (type-argument substituted) and the raw erased
    // form, plus boxing variants, so that primitive/boxed parameter forms match.
    val basePatterns = mutable.HashMap[(String, Int), mutable.ArrayBuffer[ErasedParameterPattern]]()
    val baseNames = mutable.HashSet[String]()
    val requestedKeys = overrideMethods.iterator.map(m => (m.name, m.arguments.length)).toSet
    for (view <- views.values) {
      val viewSubst: scala.collection.immutable.Map[String, Type] =
        view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap
      for (contract <- view.raw.methods) {
        if !Modifier.isStatic(contract.modifier) && !Modifier.isPrivate(contract.modifier) then
          baseNames += contract.name
          val key = (contract.name, contract.arguments.length)
          if requestedKeys.contains(key) then
            val specializedArgs =
              contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
            val patterns = basePatterns.getOrElseUpdate(key, mutable.ArrayBuffer.empty)
            patterns += parameterPattern(specializedArgs, typing)
            patterns += parameterPattern(contract.arguments, typing)
      }
    }

    for (impl <- overrideMethods) {
      val implPattern = parameterPattern(impl.arguments, typing)
      val candidates = basePatterns.get((impl.name, impl.arguments.length))
      if !candidates.exists(_.exists(implPattern.overlaps)) then
        val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
        val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
        typing.report(SemanticError.OVERRIDE_TARGET_NOT_FOUND, location, impl.name, paramDescriptor, clazz.name, baseNames.toArray)
    }
  }

  /**
   * Reports E0083 when catch clause `index`'s exception type is already covered by an
   * earlier clause in the same `try`, making it unreachable. Alternatives of the same
   * `catch e: A | B` clause are exempt from shadowing each other: the grammar desugars
   * them to entries sharing one body, but `block.copy(...)` in Rewriting gives each a
   * fresh object, so `eq` cannot tell them apart post-rewrite — compare the
   * (structurally-equal) source location instead, which `.copy` preserves. Shared by
   * both places a `try`/`catch` is typed: `TryExpressionTyping` (try-as-expression) and
   * `BlockElementLowering` (try-as-statement).
   */
  def checkUnreachableCatchClause(
    bodyContext: onion.compiler.typing.session.TypingBodyContext,
    recClauses: List[(AST.Argument, AST.BlockExpression)],
    catchTypes: Array[Type],
    index: Int,
    argument: AST.Argument,
    argType: Type,
    catchBody: AST.BlockExpression
  ): Unit = {
    var j = 0
    var shadowedBy: Type = null
    while (j < index && shadowedBy == null) {
      val earlierBody = recClauses(j)._2
      if (earlierBody.location != catchBody.location && TypeRules.isSuperType(catchTypes(j), argType)) {
        shadowedBy = catchTypes(j)
      }
      j += 1
    }
    if (shadowedBy != null) {
      bodyContext.report(SemanticError.UNREACHABLE_CATCH_CLAUSE, argument, argType, shadowedBy)
    }
  }

  def checkErasureSignatureCollisions(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    val seen = mutable.HashMap[(String, String), Method]()
    for m <- clazz.methods do
      val key = (m.name, erasedMethodDesc(m))
      if seen.contains(key) then
        val location = typing.lookupAST(m.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
        typing.report(SemanticError.ERASURE_SIGNATURE_COLLISION, location, clazz, m.name, key._2)
      else
        seen(key) = m
  }

  def checkAbstractMethodImplementation(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    // Skip if the class is abstract or an interface
    if Modifier.isAbstract(clazz.modifier) || clazz.isInterface then return

    val allViews = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz))
    // Exclude the target class itself to avoid checking methods against themselves
    val views = allViews - clazz
    if views.isEmpty then return

    // Most classes extend Object and conform to nothing, so no view has an abstract contract:
    // then there is nothing to check and the implementation map below need not be built.
    def isContract(m: Method): Boolean =
      Modifier.isAbstract(m.modifier) && !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier)
    if !views.values.exists(_.raw.methods.exists(isContract)) then return

    // Collect all implemented methods from this class AND all ancestor classes
    // Parent classes may already provide concrete implementations for abstract methods
    val implByNameAndArity = mutable.HashMap[(String, Int), mutable.ArrayBuffer[Array[String]]]()

    def registerImplementation(m: Method): Unit = {
      val candidates = implByNameAndArity.getOrElseUpdate((m.name, m.arguments.length), mutable.ArrayBuffer.empty)
      candidates += m.arguments.map(arg => Erasure.asmType(arg).getDescriptor)
    }

    // Add this class's own methods
    for m <- clazz.methods do
      if !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier) then
        registerImplementation(m)

    // Also add concrete methods from parent classes (not just interfaces)
    for view <- views.values do
      for m <- view.raw.methods do
        if !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier) then
          registerImplementation(m)

    // Check all abstract methods from superclasses and interfaces
    for (view <- views.values) {
      val viewSubst: scala.collection.immutable.Map[String, Type] =
        view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap

      for (contract <- view.raw.methods) {
        if Modifier.isAbstract(contract.modifier) && !Modifier.isStatic(contract.modifier) && !Modifier.isPrivate(contract.modifier) then
          // Apply type substitution first, then erase
          // Example: Picker[String].pick(T) → pick(String) (NOT pick(Object))
          val specializedArgs =
            contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
          val pattern = parameterPattern(specializedArgs, typing)
          val candidates = implByNameAndArity.get((contract.name, specializedArgs.length))
          if !candidates.exists(_.exists(pattern.accepts)) then
            val paramDescriptor = specializedArgs.map(_.name).mkString(", ")
            typing.report(SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD, fallback, clazz.name, contract.name, paramDescriptor)
      }
    }
  }
}
