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

  // Parameter types up to `?`, compared by name as `BridgeMethodEmitter` does for a
  // non-generic supertype.
  private def plainNames(args: Array[Type]): Seq[String] =
    args.toSeq.map {
      case nt: NullableType => nt.unwrap.name
      case other => other.name
    }

  // A parameter or return type as written in source, keeping `JInteger` apart from `Int`:
  // E0068's hint must show the difference that made the override miss (#2896).
  private val boxedAliases: Map[String, String] = Map(
    "java.lang.Integer" -> "JInteger", "java.lang.Long" -> "JLong", "java.lang.Double" -> "JDouble",
    "java.lang.Float" -> "JFloat", "java.lang.Boolean" -> "JBoolean", "java.lang.Byte" -> "JByte",
    "java.lang.Short" -> "JShort", "java.lang.Character" -> "JCharacter")
  private def sourceForm(tp: Type): String = tp match
    case nt: NullableType => sourceForm(nt.innerType) + "?"
    case ct: ClassType if !ct.isInstanceOf[AppliedClassType] && boxedAliases.contains(ct.name) => boxedAliases(ct.name)
    case other => onion.compiler.toolbox.TypeFormatting.sourceForm(other)
  private def signatureForm(name: String, args: Array[Type], ret: Type): String =
    s"$name(${args.map(sourceForm).mkString(", ")}): ${sourceForm(ret)}"

  // Mirrors `BridgeMethodEmitter.isBridgeableReturn`: the same JVM type, or a covariant reference.
  private def bridgeableReturn(inherited: Type, own: Type): Boolean =
    Erasure.asmType(inherited) == Erasure.asmType(own) ||
      (!inherited.isBasicType && !own.isBasicType && TypeRules.isSuperType(inherited, own))

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

  /**
   * An interface method with the name and full descriptor of a final `java.lang.Object` method
   * (`notify()V`, `getClass()Ljava/lang/Class;`) makes the interface fail to load with
   * `IncompatibleClassChangeError` (#2572). Report E0039 at the method, as a class gets.
   * A same-named method with another descriptor is a different method and stays legal.
   */
  def checkInterfaceFinalObjectOverrides(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    val objectClass = typing.table_.rootClass
    val finals = objectClass.methods.filter(m => Modifier.isFinal(m.modifier) && !Modifier.isStatic(m.modifier))
    if finals.nonEmpty then
      val finalDescriptors =
        finals.iterator.map(m => (m.name, Erasure.methodDescriptor(m.returnType, m.arguments))).toSet
      for (impl <- clazz.methods
           if !Modifier.isStatic(impl.modifier) && !Modifier.isPrivate(impl.modifier) &&
             finalDescriptors.contains((impl.name, Erasure.methodDescriptor(impl.returnType, impl.arguments)))) {
        val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
        val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
        typing.report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, objectClass.name)
      }
  }

  /**
   * A record skips the `java.lang.Object` contracts in [[checkOverrideContracts]], because a
   * component may be named like an Object method (`wait: Int`) and the JVM tells the two apart
   * by descriptor. A body method with the full descriptor of a final Object method (`notify()V`,
   * `getClass()Ljava/lang/Class;`) does override it, and the class then fails to load (#2533).
   * Report E0039 for exactly those; a same-named method with another descriptor still runs.
   * Only a component's own accessor (no parameters) is left to E0010 (#2518): a body overload
   * named like a component, `wait(ms: Long)` beside `wait: Int`, is checked like any other.
   */
  def checkRecordFinalObjectOverrides(
    typing: Typing,
    clazz: ClassDefinition,
    componentNames: Set[String],
    fallback: Location
  ): Unit = {
    val objectView = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz))
      .collectFirst { case (c, view) if c.name == "java.lang.Object" => view }
    objectView.foreach { view =>
      val finals = view.raw.methods.filter(m => Modifier.isFinal(m.modifier) && !Modifier.isStatic(m.modifier))
      if finals.nonEmpty then
        val finalDescriptors =
          finals.iterator.map(m => (m.name, Erasure.methodDescriptor(m.returnType, m.arguments))).toSet
        for (impl <- clazz.methods
             if !Modifier.isStatic(impl.modifier) && !Modifier.isPrivate(impl.modifier) &&
               !(impl.arguments.isEmpty && componentNames.contains(impl.name)) &&
               finalDescriptors.contains((impl.name, Erasure.methodDescriptor(impl.returnType, impl.arguments)))) {
          val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
          val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
          typing.report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, view.raw.name)
        }

      // A body method with an overridable Object method's name and parameters but an
      // incompatible return (`def toString(): Int`) lost the generated method or gave two
      // answers (#2713). A class gets E0104 for it; so does a record. A component's own
      // accessor is left to E0010, and a compatible (covariant) return is a normal override.
      val overridable = view.raw.methods.filter(m =>
        !Modifier.isFinal(m.modifier) && !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier))
      for (contract <- overridable;
           impl <- clazz.methods
           if impl.name == contract.name && !Modifier.isStatic(impl.modifier) && !Modifier.isPrivate(impl.modifier) &&
             !(impl.arguments.isEmpty && componentNames.contains(impl.name)) &&
             erasedParamDescriptor(impl.arguments) == erasedParamDescriptor(contract.arguments) &&
             impl.returnType != contract.returnType) {
        val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
        if (impl.returnType.isBasicType || contract.returnType.isBasicType)
          typing.report(SemanticError.OVERRIDE_RETURN_TYPE_MISMATCH, location, impl.name, impl.returnType, view.raw, contract.returnType)
        else if (!primitiveAwareAssignable(contract.returnType, impl.returnType, typing))
          typing.report(SemanticError.INCOMPATIBLE_TYPE, location, contract.returnType, impl.returnType)
      }
    }
  }

  def checkOverrideContracts(
    typing: Typing,
    clazz: ClassDefinition,
    fallback: Location,
    skipObject: Boolean = false,
    memberLocations: scala.collection.immutable.Map[String, Location] = scala.collection.immutable.Map.empty
  ): Unit = {
    if clazz.isInterface then return
    val allViews = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz))
    // Exclude the target class itself to avoid checking methods against themselves
    // A record keeps components named like Object's methods (`wait`, `notify`), which the JVM
    // tells apart by descriptor; only the interface and record-supertype contracts are checked.
    val views = (allViews - clazz).filter { case (c, _) => !(skipObject && c.name == "java.lang.Object") }
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
          val exactImpl = implByErasedParams.get(specializedKey).orElse(implByErasedParams.get(erasedKey))
          // A parameter differing only by `?` (`k: Int?` for `k: Int`) is the same slot, and
          // codegen would bridge it: over a final method that is E0039, as for the exact
          // types (#2896). The bridge itself is never emitted for a final target.
          if exactImpl.isEmpty && Modifier.isFinal(contract.modifier) then
            val specializedRet =
              TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)
            implByName(contract.name).find(impl =>
              impl.arguments.length == specializedArgs.length &&
                questionOnly(plainNames(specializedArgs), specializedRet, specializedArgs, true, impl)
            ).foreach { impl =>
              val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location)
                .orElse(memberLocations.get(impl.name)).getOrElse(fallback)
              val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
              typing.report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, view.raw.name)
            }
          exactImpl.foreach { impl =>
            val specializedRet =
              TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)

            // A record's synthesized accessor has no AST of its own: point at its component.
            val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location)
              .orElse(memberLocations.get(impl.name)).getOrElse(fallback)

            // Check if trying to override a final method
            if Modifier.isFinal(contract.modifier) then
              val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
              typing.report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, view.raw.name)

            specializedArgs.zip(impl.arguments).foreach { case (arg, implArg) =>
              if (!primitiveAwareSuperType(implArg, arg, typing)) {
                typing.report(SemanticError.INCOMPATIBLE_TYPE, location, arg, implArg)
              }
            }

            // The JVM overrides by descriptor, and no bridge is emitted for a non-generic
            // contract, so when either return type is primitive they must be the same type:
            // `Int` over `Double` or over `Integer` is assignable, but the method would answer
            // only calls made through its own type (#2372). A type-variable contract is erased
            // and bridged, so it keeps the assignability rule.
            // A contract inherited through a generic supertype (`G[JInteger]`) is bridged
            // even when its return is not a type variable, and the bridge boxes a
            // primitive return: `n(): Int` over `n(): JInteger` (or `Number`) works (#2549).
            // It never unboxes or widens, so `JInteger` over `Int` and `Int` over `Double`
            // still clash.
            val boxedByBridge =
              view.raw.typeParameters.nonEmpty && view.typeArguments.nonEmpty &&
                !Modifier.isAbstract(impl.modifier) && !specializedRet.isBasicType &&
                (impl.returnType match
                  case bt: BasicType if bt != BasicType.VOID =>
                    TypeRules.isSuperType(specializedRet, typing.boxedTypeArgument(bt))
                  case _ => false)
            val descriptorClash =
              !contract.returnType.isInstanceOf[TypeVariableType] && !boxedByBridge &&
                (specializedRet.isBasicType || impl.returnType.isBasicType) && specializedRet != impl.returnType
            if (descriptorClash) {
              typing.report(SemanticError.OVERRIDE_RETURN_TYPE_MISMATCH, location, impl.name, impl.returnType, view.raw, specializedRet)
            } else if (!boxedByBridge && !primitiveAwareAssignable(specializedRet, impl.returnType, typing)) {
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
   * An implementation counts as overriding a base method when they share the same
   * name and the same erased parameter descriptor, or when codegen bridges the two
   * (`BridgeMethodEmitter`): through an applied generic supertype a primitive/boxed
   * difference counts; through a non-generic one only a difference in `?`
   * (`k: Int?` for `k: Int`), with a return the bridge can pass on (#2896). Only
   * when NO base method matches is the override rejected.
   */
  def checkOverrideTargets(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    if clazz.isInterface then return

    // Collect non-static, non-private instance methods declared here: the override-marked
    // ones are checked for a target, the others for a `?`-only override (W0029).
    val instanceMethods = clazz.methods.filter(m => !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier))
    val (overrideMethods, unmarkedMethods) = instanceMethods.partition(m => AST.hasModifier(m.modifier, AST.M_OVERRIDE))
    if instanceMethods.isEmpty then return

    val allViews = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz))
    // Exclude the target class itself: an override must target a *base* member.
    val views = allViews - clazz

    // Index actual base methods by name and arity, keeping per-position choices.
    // Include both the specialized (type-argument substituted) and the raw erased
    // form, plus boxing variants, so that primitive/boxed parameter forms match.
    // One entry per base method: its boxing-aware patterns and its exact descriptors.
    // `viaBridge`: the supertype is applied (`G[JInteger]`), so codegen bridges a boxed or
    // primitive form to it. A non-generic supertype is bridged only for a `?` difference
    // (`plainNames`, with `returnType`); `dec(JInteger)` beside an inherited `dec(Int)` is an
    // overload that never runs through the base type (#2896).
    // `concrete`: the base method has a body; `args`: its parameters as declared.
    final case class BaseContract(
      patterns: Seq[ErasedParameterPattern], exact: Set[String], viaBridge: Boolean,
      plainNames: Seq[String], returnType: Type, signature: String,
      args: Array[Type], concrete: Boolean, isFinal: Boolean, owner: String)
    val baseContracts = mutable.HashMap[(String, Int), mutable.ArrayBuffer[BaseContract]]()
    val baseNames = mutable.HashSet[String]()
    val requestedKeys = instanceMethods.iterator.map(m => (m.name, m.arguments.length)).toSet
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
            baseContracts.getOrElseUpdate(key, mutable.ArrayBuffer.empty) += BaseContract(
              Seq(parameterPattern(specializedArgs, typing), parameterPattern(contract.arguments, typing)),
              Set(erasedParamDescriptor(specializedArgs), erasedParamDescriptor(contract.arguments)),
              viaBridge = viewSubst.nonEmpty,
              plainNames = plainNames(contract.arguments), returnType = contract.returnType,
              signature = signatureForm(contract.name, specializedArgs,
                TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)),
              args = contract.arguments, concrete = !Modifier.isAbstract(contract.modifier),
              isFinal = Modifier.isFinal(contract.modifier), owner = view.raw.name)
      }
    }

    for (impl <- overrideMethods) {
      val implPattern = parameterPattern(impl.arguments, typing)
      val implExact = erasedParamDescriptor(impl.arguments)
      val candidates = baseContracts.getOrElse((impl.name, impl.arguments.length), mutable.ArrayBuffer.empty[BaseContract])
      // A sibling that matches a base method exactly is that method's real override, so an
      // `override` reaching the same base method only through boxing overrides nothing (#2681).
      // Judged per base method: a sibling matching a different overload does not count (#2921).
      def exactSibling(c: BaseContract): Boolean =
        clazz.methods.exists(o => (o ne impl) && o.name == impl.name && !Modifier.isStatic(o.modifier) &&
          o.arguments.length == impl.arguments.length && c.exact.contains(erasedParamDescriptor(o.arguments)))
      def bridged(c: BaseContract): Boolean =
        if c.viaBridge then c.patterns.exists(implPattern.overlaps)
        else questionOnly(c.plainNames, c.returnType, c.args, c.concrete, impl)
      val overridesSomething = candidates.exists(c =>
        c.exact.contains(implExact) || (bridged(c) && !exactSibling(c)))
      if !overridesSomething then
        val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
        val paramDescriptor = impl.arguments.map(sourceForm).mkString(", ")
        // Same name and arity in a supertype: show what the override would have to match.
        val nearMisses = candidates.iterator.map(_.signature).distinct.toArray
        typing.report(SemanticError.OVERRIDE_TARGET_NOT_FOUND, location, impl.name, paramDescriptor, clazz.name, baseNames.toArray, nearMisses)
    }

    // An unmarked method that takes over an inherited method's code only because the two
    // differ by `?` (`dec(k: Int?)` beside `Base.dec(k: Int)`): it overrides, so a call
    // through the base runs it. Say so, since nothing in the source does (W0029).
    if typing.reportingEnabled then
      for impl <- unmarkedMethods do
        val implExact = erasedParamDescriptor(impl.arguments)
        val candidates = baseContracts.getOrElse((impl.name, impl.arguments.length), mutable.ArrayBuffer.empty[BaseContract])
        // A slot of its own type inherited (row 3, a chain, or an abstract one): not this case.
        // Nor when the class declares the base method's exact types itself (`f(Int)` beside
        // `f(Int?)`): that sibling overrides the base, and this one is an overload (row 12).
        def exactSibling(c: BaseContract): Boolean =
          clazz.methods.exists(o => (o ne impl) && o.name == impl.name && !Modifier.isStatic(o.modifier) &&
            o.arguments.length == impl.arguments.length && c.exact.contains(erasedParamDescriptor(o.arguments)))
        if !candidates.exists(_.exact.contains(implExact)) then
          candidates.find(c => !c.viaBridge && c.concrete && !c.isFinal && !exactSibling(c) &&
            questionOnly(c.plainNames, c.returnType, c.args, c.concrete, impl)).foreach { c =>
            val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
            val own = s"${impl.name}(${impl.arguments.map(sourceForm).mkString(", ")})"
            typing.warningReporter_.setSourceFile(typing.unit_.sourceFile)
            typing.warningReporter_.report(WarningCategory.UnmarkedNullableOverride, location,
              s"'$own' overrides '${c.signature}' of ${c.owner}, because the two differ only by '?'; " +
                "a call through the base type runs it. Mark it 'override', or rename it to keep a separate method")
          }
  }

  // Whether codegen bridges `impl` to the non-generic base method `c` (the same name, the
  // same parameter types up to `?`), as `BridgeMethodEmitter` does: the return must pass
  // through, and an own `f(Int)` beside a concrete `f(Int?)` is bridged only with a
  // reference return, a `null` through the base would otherwise throw (#2896, row 10).
  private def questionOnly(
    basePlainNames: Seq[String], baseReturn: Type, baseArgs: Array[Type], baseConcrete: Boolean, impl: Method
  ): Boolean =
    basePlainNames == plainNames(impl.arguments) && bridgeableReturn(baseReturn, impl.returnType) &&
      !(baseConcrete && unboxes(impl.arguments, baseArgs) && !referenceCovariant(baseReturn, impl.returnType))

  private def unboxes(own: Array[Type], inherited: Array[Type]): Boolean =
    own.indices.exists(i => isPrimitiveJvm(own(i)) && !isPrimitiveJvm(inherited(i)))

  private def isPrimitiveJvm(tp: Type): Boolean =
    val sort = Erasure.asmType(tp).getSort
    sort != org.objectweb.asm.Type.OBJECT && sort != org.objectweb.asm.Type.ARRAY

  private def referenceCovariant(inherited: Type, own: Type): Boolean =
    !inherited.isBasicType && !own.isBasicType && TypeRules.isSuperType(inherited, own)

  /**
   * Reports E0083 when catch clause `index`'s exception type is already covered by an
   * earlier clause in the same `try`, making it unreachable. Alternatives of the same
   * `catch e: A | B` clause are exempt from shadowing each other: the grammar desugars
   * them to entries sharing one body, but `block.copy(...)` in Rewriting gives each a
   * fresh object, so `eq` cannot tell them apart post-rewrite — compare the
   * (structurally-equal) source location instead, which `.copy` preserves. Shared by
   * both places a `try`/`catch` is typed: `TryExpressionTyping` (try-as-expression) and
   * `BlockElementLowering` (try-as-statement).
   *
   * `validTypes` flags which clauses actually passed the sibling "must be a Throwable"
   * check (E0000/INCOMPATIBLE_TYPE). A clause whose own type is illegal is never a real
   * shadow candidate (its "fix" of moving another clause after it is nonsensical, since
   * the illegal clause itself doesn't compile), and an illegal clause's own reachability
   * is not worth reporting on top of the type error it already has.
   */
  def checkUnreachableCatchClause(
    bodyContext: onion.compiler.typing.session.TypingBodyContext,
    recClauses: List[(AST.Argument, AST.BlockExpression)],
    catchTypes: Array[Type],
    validTypes: Array[Boolean],
    index: Int,
    argument: AST.Argument,
    argType: Type,
    catchBody: AST.BlockExpression
  ): Unit = {
    if (!validTypes(index)) return
    var j = 0
    var shadowedBy: Type = null
    while (j < index && shadowedBy == null) {
      val earlierBody = recClauses(j)._2
      if (validTypes(j) && earlierBody.location != catchBody.location && TypeRules.isSuperType(catchTypes(j), argType)) {
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

  /** The JVM identity of a method: its name and erased descriptor. Two methods with the same
    * key cannot both be emitted into one class file. */
  def erasedSignatureKey(m: Method): (String, String) = (m.name, erasedMethodDesc(m))

  /** The JVM descriptor of a constructor (`(Ljava/lang/Object;)V`). Two constructors with
    * the same one cannot both be emitted into one class file. */
  def erasedConstructorDescriptor(c: ConstructorRef): String =
    Erasure.methodDescriptor(BasicType.VOID, c.getArgs)

  /** A concrete class whose interfaces give the same method two or more unrelated defaults, and
    * which declares or inherits no class method of that signature to settle it, is E0116. The
    * JVM finds no single maximally specific default, so the call throws AbstractMethodError
    * (#2835); javac rejects the class as "inherits unrelated defaults". A default that a more
    * specific interface overrides (`interface B conforms A`) does not count. */
  def checkConflictingInterfaceDefaults(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    if Modifier.isAbstract(clazz.modifier) || clazz.isInterface then return
    val views = typing.table_.appliedViewsOf(clazz)(AppliedTypeViews.collectAppliedViewsFrom(clazz)) - clazz
    val interfaces = views.keys.filter(_.isInterface).toSeq
    if interfaces.size < 2 then return

    def isInstance(m: Method): Boolean = !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier)
    // Interface members by JVM identity: who declares each, and whether that declaration has a body.
    val declarers = mutable.LinkedHashMap[(String, String), mutable.ArrayBuffer[(ClassType, Method)]]()
    for iface <- interfaces; m <- iface.methods if isInstance(m) do
      declarers.getOrElseUpdate(erasedSignatureKey(m), mutable.ArrayBuffer.empty) += ((iface, m))
    if !declarers.values.exists(_.count(d => !Modifier.isAbstract(d._2.modifier)) >= 2) then return

    // A method in the class or a superclass wins over every interface default.
    val classChain = mutable.HashSet[(String, String)]()
    for m <- clazz.methods if isInstance(m) do classChain += erasedSignatureKey(m)
    for (tp, _) <- views if !tp.isInterface; m <- tp.methods if isInstance(m) do classChain += erasedSignatureKey(m)

    for (key, ds) <- declarers if !classChain.contains(key) do
      // Maximally specific: no other declaring interface is a subinterface of this one.
      val specific = ds.filter { case (iface, _) =>
        !ds.exists { case (other, _) => (other ne iface) && TypeRules.isSuperType(iface, other) && !TypeRules.isSuperType(other, iface) }
      }
      val withBody = specific.filter(d => !Modifier.isAbstract(d._2.modifier)).map(_._1).distinct
      if withBody.size >= 2 then
        val m = specific.head._2
        val params = m.arguments.map(_.name).mkString(", ")
        val owners = withBody.map(_.name).sorted.mkString(", ")
        typing.report(SemanticError.CONFLICTING_INTERFACE_DEFAULTS, fallback, clazz.name, m.name, params, owners)
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
    // Each implementation's parameters: erased descriptors, and the types without `?`.
    final case class Impl(descs: Array[String], plain: Array[String], method: Method, args: Array[Type])
    val implByNameAndArity = mutable.HashMap[(String, Int), mutable.ArrayBuffer[Impl]]()

    def descriptorsOf(args: Array[Type]): Array[String] = args.map(arg => Erasure.asmType(arg).getDescriptor)

    def registerImplementation(m: Method, args: Array[Type]): Unit = {
      val candidates = implByNameAndArity.getOrElseUpdate((m.name, m.arguments.length), mutable.ArrayBuffer.empty)
      candidates += Impl(descriptorsOf(args), plainNames(args).toArray, m, args)
    }

    // Add this class's own methods
    for m <- clazz.methods do
      if !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier) then
        registerImplementation(m, m.arguments)

    // Also add concrete methods from parent classes (not just interfaces)
    // Also register each under the view's type arguments: `Base[String]`'s `apply(T)` implements
    // an inherited `Foo[String].apply(String)` even though it erases to `apply(Object)`.
    for view <- views.values do
      val implSubst: scala.collection.immutable.Map[String, Type] =
        view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap
      for m <- view.raw.methods do
        if !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier) then
          registerImplementation(m, m.arguments)
          if implSubst.nonEmpty then
            registerImplementation(m, m.arguments.map(arg =>
              TypeSubstitution.substituteType(arg, implSubst, emptyMethodSubst, defaultToBound = true)))

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
          // A primitive/boxed difference is bridged only against a generic supertype
          // (`n(x: Int)` for `G[JInteger]`'s `n(x: T)`, #2559). Against a non-generic one,
          // `n(x: Int)` beside `n(x: JInteger)` is an overload (#2896's row 5): nothing
          // implements the contract, and the call threw AbstractMethodError (#2922). Only a
          // parameter that differs by `?` alone (`Int?` for `Int`) is bridged there.
          val contractDescs = descriptorsOf(specializedArgs)
          val contractPlain = plainNames(specializedArgs).toArray
          def bridged(impl: Impl): Boolean =
            viewSubst.nonEmpty || impl.descs.indices.forall(i =>
              impl.descs(i) == contractDescs(i) || impl.plain(i) == contractPlain(i))
          val candidates = implByNameAndArity.getOrElse((contract.name, specializedArgs.length), mutable.ArrayBuffer.empty)
          val matching = candidates.filter(impl => pattern.accepts(impl.descs))
          if !matching.exists(bridged) then
            val paramDescriptor = specializedArgs.map(_.name).mkString(", ")
            def shown(args: Array[Type]): String = s"${contract.name}(${args.map(sourceForm).mkString(", ")})"
            matching.headOption match
              case Some(nearMiss) =>
                typing.report(SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD, fallback, clazz.name, contract.name, paramDescriptor,
                  shown(nearMiss.args), shown(specializedArgs))
              case None =>
                typing.report(SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD, fallback, clazz.name, contract.name, paramDescriptor)
      }
    }
  }
}
