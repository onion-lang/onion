package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.{TypingBodyContext, TypingUnitContext}

private[compiler] final class MethodBodySupport(
  typing: Typing,
  unitContext: TypingUnitContext,
  bodyContext: TypingBodyContext,
  typed: (AST.Expression, LocalContext, Type) => Option[Term],
  typedTerms: (Array[AST.Expression], LocalContext) => Array[Term],
  translate: (AST.BlockElement, LocalContext) => ActionStatement,
  addReturnNode: (ActionStatement, Type) => StatementBlock,
  processAssignable: (AST.Node, Type, Term) => Term,
  resolveConstructorWithNamedArgs: (AST.Node, List[AST.Expression], ClassType, LocalContext) =>
    Option[(ConstructorDefinition, Array[Term], Array[AST.Node])]
) {
  def processMethodLikeBody(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression,
    requires: List[AST.Expression] = Nil,
    ensures: List[AST.Expression] = Nil
  ): Unit = {
    val context = prepareMethodContext(method, args, block)
    // Preconditions see the parameters and the object, before the body runs.
    if (requires.nonEmpty) {
      method.preconditions = typeContractClauses(requires, context, ContractScope.Pre)
      method.contractRequiresText = requires.flatMap(onion.compiler.contracts.ContractText.write)
    }
    val triesBefore = bodyContext.tryCount
    val translated = translate(block, context)
    method.mayContainTry = bodyContext.tryCount != triesBefore
    checkMissingReturn(method.returnType, translated, method.name, block)
    // Postconditions are typed in the same context once the body is, with `result` bound.
    if (ensures.nonEmpty) {
      method.postconditions = typePostconditions(method, ensures, context)
      method.contractEnsuresText = ensures.flatMap(onion.compiler.contracts.ContractText.write)
      method.assertsResult = ensures.exists { case AST.Id(_, "result") => true; case _ => false }
    }
    // `@Trusted` (RFC #2557): the contract is relied on and not verified. Every use is listed, because
    // each is a claim that nothing checked.
    if (method.annotations.contains("Trusted") && (requires.nonEmpty || ensures.nonEmpty))
      bodyContext.warningReporter.report(WarningCategory.ContractTrusted, block.location,
        s"the contract of '${method.name}' is trusted, not verified: its body is not checked, and callers rely on it")
    val definition = method.classType match { case d: ClassDefinition => Some(d); case _ => None }
    val classClauses = definition.exists(d => d.invariantSources.nonEmpty || d.historySources.nonEmpty)
    // A public instance method of a class with an `invariant` or a `history` is a place where it must
    // hold, so it is guarded too (RFC #2557, design 7.4). A constructor is not a method here.
    val isStatic = (method.modifier & AST.M_STATIC) != 0
    val needsGuard = ensures.nonEmpty || (classClauses && !isStatic && (method.modifier & AST.M_PUBLIC) != 0)
    val refusal = EnsuresGuardSupport.refusal(method, ensures)
    val splitGuards = bodyContext.contractMode == ContractMode.Runtime && needsGuard &&
      (ensures.isEmpty || method.postconditions.size == ensures.size) && refusal.isEmpty
    if (bodyContext.contractMode == ContractMode.Runtime && needsGuard && !splitGuards)
      warnNotGuarded(method, block, refusal.getOrElse("the clause could not be typed"))
    // With an `ensures` the body moves to `m$body` and `m` becomes the guard; otherwise a `requires`
    // is guarded in place at the top of the body.
    val guarded = if (splitGuards) translated else withRequiresGuards(method, requires, translated)
    val translatedBlock = addReturnNode(withPrologue(context, guarded), method.returnType)
    if (splitGuards) splitIntoBodyAndGuard(method, args, block, requires, ensures, translatedBlock, context)
    else {
      method.setBlock(translatedBlock)
      method.setFrame(context.getContextFrame)
    }
    reportUnused(context)
  }

  /** W0026: a clause that `--contracts runtime` does not enforce. Never silent. */
  private def warnNotGuarded(method: MethodDefinition, at: AST.Node, why: String): Unit =
    bodyContext.warningReporter.report(WarningCategory.ContractNotEnforced, at.location,
      s"the contract of '${method.name}' is not enforced at run time: $why")

  /**
   * The body keeps `context`'s frame and becomes the private method `name$body`. The method itself
   * gets a new frame with the same parameters in the same slots, and the guard as its body.
   */
  private def splitIntoBodyAndGuard(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression,
    requires: List[AST.Expression],
    ensures: List[AST.Expression],
    body: StatementBlock,
    context: LocalContext
  ): Unit = {
    val classType = method.classType
    val isStatic = (method.modifier & AST.M_STATIC) != 0
    val bodyMethod = new MethodDefinition(method.location, AST.M_PRIVATE | (if (isStatic) AST.M_STATIC else 0), classType, method.name + "$body",
      method.arguments, method.returnType, body, method.typeParameters, method.throwsTypes, method.isVararg)
    bodyMethod.mayContainTry = method.mayContainTry
    bodyMethod.setFrame(context.getContextFrame)
    bodyMethod.setArgumentsWithDefaults(method.argumentsWithDefaults)
    classType.asInstanceOf[ClassDefinition].add(bodyMethod)

    // The guard: a fresh context with the same parameters, so `requires` and `ensures` are typed over
    // its own frame (and `result` gets a slot of its own).
    val gctx = prepareMethodContext(method, args, block)
    val void = method.returnType == BasicType.VOID
    val argTerms: Array[Term] = args.map { a =>
      val b = gctx.lookup(a.name)
      new RefLocal(b.frameIndex, b.index, b.tp): Term
    }.toArray
    // A static method calls its body statically; there is no object, so no `this` to pass.
    val call: Term =
      if (isStatic) new CallStatic(method.location, classType, bodyMethod, argTerms)
      else new Call(method.location, new This(method.location, classType), bodyMethod, argTerms)
    val saves = scala.collection.mutable.ArrayBuffer.empty[(Int, Term)]
    val requiresTerms = if (requires.nonEmpty) typeContractClauses(requires, gctx, ContractScope.Pre) else Nil
    gctx.oldSaves = Some(saves)
    val (resultSlot, ensuresTerms) =
      if (void) (-1, typeContractClauses(ensures, gctx, ContractScope.Post))
      else gctx.openScope {
        val slot = gctx.add("result", method.returnType, false)
        (slot, typeContractClauses(ensures, gctx, ContractScope.Post))
      }
    val strings = bodyContext.load("java.lang.String")
    val violation = bodyContext.load("onion.ContractViolation")
    val ctor = violation.findConstructor(Array[Term](new StringValue(null, "", strings)))
    def guard(clause: Term, source: AST.Expression, what: String): ActionStatement = {
      val keyword = if (what == "precondition") "requires" else if (what == "postcondition") "ensures" else if (what.startsWith("invariant")) "invariant" else "history"
      val message = s"${classType.name}.${method.name}() $what failed: $keyword ${onion.compiler.contracts.ExpressionText.of(source)}"
      val fail = new Throw(clause.location, new NewObject(clause.location, ctor(0), Array[Term](new StringValue(clause.location, message, strings))))
      new IfStatement(clause.location, new UnaryTerm(clause.location, UnaryTerm.Kind.NOT, BasicType.BOOLEAN, clause), fail, null)
    }
    val definition = classType.asInstanceOf[ClassDefinition]
    // The class's `invariant`: no local in a typed clause, so the clauses typed once are shared. The
    // `history` is typed again over this frame, because its `old(field)` is saved at entry. That is
    // done before `saved` is built, since typing it adds to the saves.
    // A class's `invariant` and `history` speak about an object, so a static method has neither.
    val invariantTerms = if (isStatic) Seq.empty else definition.invariants.zip(definition.invariantSources)
    val historyTerms = if (isStatic) Nil else typeContractClauses(definition.historySources, gctx, ContractScope.History)
    val pre: Seq[ActionStatement] = requiresTerms.zip(requires).map { case (t, s) => guard(t, s, "precondition") } ++
      invariantTerms.map { case (t, s) => guard(t, s, "invariant (on entry)") }
    val ensuresGuards: Seq[ActionStatement] = ensuresTerms.zip(ensures).map { case (t, s) => guard(t, s, "postcondition") }
    if (ensuresGuards.nonEmpty && !isStatic)
      definition.ensuresGuards((method.name, method.location)) = ensuresGuards.map(_.asInstanceOf[IfStatement])
    val post: Seq[ActionStatement] = ensuresGuards ++
      invariantTerms.map { case (t, s) => guard(t, s, "invariant (on exit)") } ++
      historyTerms.zip(if (isStatic) Nil else definition.historySources).map { case (t, s) =>
        val g = guard(t, s, "history")
        definition.historyGuards((method.name, onion.compiler.contracts.ExpressionText.of(s))) = g.asInstanceOf[IfStatement]
        g
      }
    // `old(e)` is e on entry: assigned after `requires` holds and before the body runs.
    val saved: Seq[ActionStatement] = saves.toSeq.map { case (slot, term) =>
      new ExpressionActionStatement(term.location, new SetLocal(0, slot, term.`type`, term))
    }
    val statements: Seq[ActionStatement] =
      if (void) pre ++ saved ++ Seq(new ExpressionActionStatement(method.location, call)) ++ post :+ new Return(method.location, null)
      else pre ++ saved ++ Seq(new ExpressionActionStatement(method.location, new SetLocal(0, resultSlot, method.returnType, call))) ++ post :+
        new Return(method.location, new RefLocal(0, resultSlot, method.returnType))
    method.setBlock(new StatementBlock(method.location, statements*))
    method.setFrame(gctx.getContextFrame)
  }

  /**
   * `--contracts runtime` (RFC #2557, design 7.4): each `requires` clause becomes a guard at the
   * top of the body, `if !(clause) throw new ContractViolation("...")`. A violation is the caller's
   * fault, so it is thrown before any of the body runs. With any other mode the body is unchanged,
   * so a program compiled without the flag is byte-for-byte what it was.
   */
  private def withRequiresGuards(method: MethodDefinition, sources: List[AST.Expression], body: ActionStatement): ActionStatement =
    if (bodyContext.contractMode != ContractMode.Runtime || method.preconditions.isEmpty) body
    else {
      val violation = bodyContext.load("onion.ContractViolation")
      val ctor = violation.findConstructor(Array[Term](new StringValue(null, "", bodyContext.load("java.lang.String"))))
      val guards: Seq[ActionStatement] = method.preconditions.zip(sources).map { case (clause, source) =>
        val message = s"${method.classType.name}.${method.name}() precondition failed: requires ${onion.compiler.contracts.ExpressionText.of(source)}"
        val text = new StringValue(clause.location, message, bodyContext.load("java.lang.String"))
        val fail = new Throw(clause.location, new NewObject(clause.location, ctor(0), Array[Term](text)))
        new IfStatement(clause.location, new UnaryTerm(clause.location, UnaryTerm.Kind.NOT, BasicType.BOOLEAN, clause), fail, null)
      }
      new StatementBlock(body.location, (guards :+ body)*)
    }

  /**
   * The clauses of a method with no body: an interface method or an `abstract def` (RFC #2557,
   * #2577). They are typed in a context holding only the parameters and the object, the same
   * scopes a concrete method's clauses see, and kept on the method so the purity pass checks
   * them too. Nothing else about the method changes: it still has no body or frame.
   */
  def processBodilessContracts(
    method: MethodDefinition,
    args: List[AST.Argument],
    requires: List[AST.Expression],
    ensures: List[AST.Expression]
  ): Unit = {
    val context = new LocalContext
    if (method.modifier & AST.M_STATIC) != 0 then context.setStatic(true)
    context.setMethod(method)
    bindParameters(context, args, method.arguments, null)
    if (requires.nonEmpty) method.preconditions = typeContractClauses(requires, context, ContractScope.Pre)
    if (ensures.nonEmpty) {
      method.postconditions = typePostconditions(method, ensures, context)
      method.assertsResult = ensures.exists { case AST.Id(_, "result") => true; case _ => false }
    }
  }

  /**
   * Types contract clauses as Boolean expressions in `context`, marked with `scope` so that
   * `old(e)` and `result` resolve only where they are allowed (RFC #2557). A clause that fails
   * to type is reported and dropped. With `--contracts off` a clause is an error (E0105)
   * instead of being ignored, and nothing is typed.
   */
  private[typing] def typeContractClauses(
    clauses: List[AST.Expression],
    context: LocalContext,
    scope: ContractScope,
    keyword: String = null
  ): Seq[Term] = {
    val word = if (keyword != null) keyword else scope match {
      case ContractScope.Pre       => "requires"
      case ContractScope.Post      => "ensures"
      case ContractScope.Invariant => "invariant"
      case ContractScope.History   => "history"
    }
    if (bodyContext.contractMode == ContractMode.Off) {
      clauses.foreach(c => bodyContext.report(CONTRACTS_NOT_ENABLED, c, word))
      Nil
    } else {
      val saved = context.contractScope
      context.contractScope = Some(scope)
      try clauses.flatMap { c =>
        typed(c, context, BasicType.BOOLEAN).flatMap { t =>
          Option(processAssignable(c, BasicType.BOOLEAN, t))
        }
      } finally context.contractScope = saved
    }
  }

  /**
   * The `invariant` and `history` clauses of a type body (RFC #2557), typed in a context that
   * has `this`, so a bare field name is a field of the object. A `history` also sees `old(e)`
   * for the earlier state. With contracts off, each clause is E0105 instead.
   */
  def processTypeContracts(
    definition: ClassDefinition,
    invariants: List[AST.Expression],
    histories: List[AST.Expression]
  ): Unit = {
    if (invariants.nonEmpty || histories.nonEmpty) {
      val context = new LocalContext
      context.setStatic(false)
      definition.invariants = typeContractClauses(invariants, context, ContractScope.Invariant)
      definition.histories = typeContractClauses(histories, context, ContractScope.History)
      definition.invariantSources = invariants
      definition.historySources = histories
      for (h <- histories) HistoryPreorder.classify(h) match {
        case HistoryPreorder.NotPreorder(reason) => bodyContext.report(SemanticError.HISTORY_NOT_PREORDER, h, reason)
        case _ =>
      }
    }
  }

  /** `ensures`: the parameters, the object, `old(e)`, and `result` unless the method is `void`. */
  private def typePostconditions(method: MethodDefinition, clauses: List[AST.Expression], context: LocalContext): Seq[Term] =
    if (method.returnType == BasicType.VOID || bodyContext.contractMode == ContractMode.Off)
      typeContractClauses(clauses, context, ContractScope.Post)
    else
      // `result` lives in a scope of its own, so it is gone again after the clauses.
      context.openScope {
        context.add("result", method.returnType, false)
        typeContractClauses(clauses, context, ContractScope.Post)
      }

  /**
   * Reports MISSING_RETURN when a value-returning method body can complete
   * normally without returning. Onion block bodies use an explicit `return`
   * (the `= expr` form is the expression body); a body that neither returns on
   * every path nor throws silently yields the JVM default (0 / null), so we
   * reject it. [[definitelyReturns]] is conservative (true only when certain),
   * so a method that really does return on all paths is never rejected.
   */
  private def checkMissingReturn(returnType: Type, translated: ActionStatement, name: String, at: AST.Node): Unit =
    if (returnType != BasicType.VOID && !returnType.isBottomType && !definitelyReturns(translated))
      bodyContext.report(MISSING_RETURN, at, name, returnType)

  private def definitelyReturns(stmt: ActionStatement): Boolean = stmt match {
    case _: Return => true
    case _: Throw => true
    case b: StatementBlock =>
      val ss = b.statements
      ss.nonEmpty && definitelyReturns(ss(ss.length - 1))
    case i: IfStatement =>
      i.elseStatement != null && definitelyReturns(i.thenStatement) && definitelyReturns(i.elseStatement)
    case loop: ConditionalLoop =>
      // An infinite loop (while/for over a constant-true or absent condition)
      // never completes normally. A `break` would make this a false negative,
      // which is safe (we never wrongly reject), so we do not scan for one.
      loop.condition == null || (loop.condition match { case b: BoolValue => b.value; case _ => false })
    case t: Try =>
      (t.finallyStatement != null && definitelyReturns(t.finallyStatement)) ||
        (definitelyReturns(t.tryStatement) && t.catchStatements.forall(definitelyReturns))
    case s: Synchronized =>
      // A synchronized block returns exactly when its body does; without this a
      // `synchronized(l) { return x }` was wrongly flagged as missing a return.
      definitelyReturns(s.statement)
    case e: ExpressionActionStatement => e.term.`type`.isBottomType
    case _ => false
  }

  def prepareConstructorContext(constructor: ConstructorDefinition, args: List[AST.Argument], block: AST.Node): LocalContext = {
    val context = new LocalContext
    context.setConstructor(constructor)
    val paramTypes = constructor.getArgs
    block match
      case b: AST.BlockExpression => markCapturedVariables(context, args, b)
      case _ =>
    bindParameters(context, args, paramTypes, block)
    predefinedConstructorDefaults.get(constructor) match
      case Some((withDefaults, used)) =>
        used.foreach(context.recordUsage)
        constructor.setArgumentsWithDefaults(withDefaults)
      case None =>
        constructor.setArgumentsWithDefaults(
          buildArgumentsWithDefaults(args, paramTypes, context.recordUsage, constructorDefaultOwner(constructor)))
    context
  }

  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit =
    typing.kernelNodeOf[ConstructorDefinition](node).foreach { constructor =>
      val context = prepareConstructorContext(constructor, node.args, node.block)
      val targetClass = if node.selfDelegation then bodyContext.definition else bodyContext.definition.superClass
      // The object does not exist while its super/self initializer arguments are being
      // evaluated. A field read there -- `def this : this(seed)` where `seed` is a field,
      // or an explicit `this` -- is a GETFIELD on `uninitializedThis`, which the JVM
      // verifier rejects; it used to surface as an internal error at class load. Closure
      // bodies are exempt: they capture `this` and run later, when it does exist.
      def reportThisBeforeDelegation(terms: Array[Term], sources: Seq[AST.Node]): Unit =
        terms.zip(sources).collectFirst {
          case (term, source) if term != null && source != null &&
            TermWalk.exists(term) { case _: This => true; case _ => false } => source
        }.foreach(source => bodyContext.report(THIS_BEFORE_CONSTRUCTOR_DELEGATION, source, bodyContext.definition))
      if ArgumentHelpers.hasNamedArguments(node.superInits) then
        // `extends A(b = 7, a = 3)` / `: this(b = 8)`: bind by name and fill defaults,
        // exactly as `new A(b = 7, a = 3)` does; the positional path below would put
        // 7 into `a` (#2659).
        context.withArgumentSlots(resolveConstructorWithNamedArgs(node, node.superInits, targetClass, context)).foreach {
          case (ctor, params, sources) =>
            reportThisBeforeDelegation(params, sources.toSeq)
            val init = new Super(targetClass, ctor.getArgs, params, node.selfDelegation)
            finishConstructorBody(constructor, init, node.block, context, if node.primary then node.primaryAssignments else 0)
        }
      else
        val params0 = context.withArgumentSlots(typedTerms(node.superInits.toArray, context))
        if params0 != null then reportThisBeforeDelegation(params0, node.superInits)
        val matched0 = targetClass.findConstructor(params0)
        // Exact matching is substitution-blind: an applied Box[Int] still exposes
        // the constructor as Box(T) over the erased bound (Object), so a primitive
        // argument (Int) does not match. Retry against the type-argument-
        // substituted signatures with primitive boxing so that
        // `class IntBox(v: Int) : Box[Int](v)` boxes v into T, mirroring what the
        // `new Box[Int](42)` path already does (ConstructionTyping.findConstructorWithBoxing).
        val (matched, params) =
          if matched0.nonEmpty || params0 == null then (matched0, params0)
          else findSuperConstructorWithBoxing(targetClass, params0)
        if matched.length == 0 then
          bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, targetClass, termTypes(params), targetClass.constructors)
        else if matched.length > 1 then
          bodyContext.report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](targetClass, termTypes(params)), Array[AnyRef](targetClass, termTypes(params)))
        else
          warnNullSuperArguments(targetClass, matched(0), node.superInits, params)
          val init = new Super(targetClass, matched(0).getArgs, Boxing.adaptArguments(bodyContext.table, params, matched(0).getArgs), node.selfDelegation)
          finishConstructorBody(constructor, init, node.block, context, if node.primary then node.primaryAssignments else 0)
      reportConstructorOnly(context)
    }

  /** W0012 for a `null` argument to an Onion-declared super/self constructor's
   *  non-null parameter, as `new P(null)` already warns (#2477). A Java
   *  constructor's parameter nullness is unknown, so it stays silent (#2116).
   *  The parent's type arguments are substituted first, so `extends G[String?](null)`
   *  is silent and `extends G[String](null)` names `String`; a type variable that
   *  admits null (a bare `[T]`, as in `: this(null)` inside `class D[T]`) is silent too. */
  private def warnNullSuperArguments(targetClass: ClassType, ctor: ConstructorRef, args: List[AST.Expression], params: Array[Term]): Unit =
    if ctor.isInstanceOf[ConstructorDefinition] then
      val classSubst = TypeSubstitution.classSubstitution(targetClass)
      val formals = ctor.getArgs.map(t =>
        TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
      def admitsNull(f: Type): Boolean = f match
        case tv: TypeVariableType => tv.acceptsNullable
        case _ => f.isNullable || f.isNullType
      args.zip(params).zip(formals).foreach { case ((arg, p), f) =>
        if p != null && p.`type`.isNullType && !f.isBasicType && !admitsNull(f) then
          bodyContext.warningReporter.setSourceFile(bodyContext.sourceFile)
          bodyContext.warningReporter.nullToNonNullable(arg.location, f.displayName)
      }

  /**
   * Boxing-aware super-constructor fallback: substitutes the parent's class type
   * arguments into each constructor signature, matches with primitive boxing,
   * and (for the unique match) boxes the primitive argument terms so a primitive
   * type argument (Box[Int]) resolves the erased-Object parent constructor.
   * Mirrors ConstructionTyping.findConstructorWithBoxing for the super path.
   */
  private def findSuperConstructorWithBoxing(
    targetClass: ClassType,
    params: Array[Term]
  ): (Array[ConstructorRef], Array[Term]) = {
    val classSubst = TypeSubstitution.classSubstitution(targetClass)
    def substitutedArgs(c: ConstructorRef): Array[Type] =
      c.getArgs.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    val candidates = targetClass.constructors.filter { c =>
      val formals = substitutedArgs(c)
      formals.length == params.length &&
        formals.indices.forall(i => TypeRelations.isAssignableWithBoxing(formals(i), params(i).`type`, bodyContext.table))
    }
    if (candidates.length != 1) (candidates, params)
    else {
      val formals = substitutedArgs(candidates(0))
      val adapted = params.zip(formals).map { (p, f) =>
        if (!f.isBasicType && p.isBasicType) Boxing.boxForSlot(bodyContext.table, p, f)
        else p
      }
      (candidates, adapted)
    }
  }

  def finishConstructorBody(
    constructor: ConstructorDefinition,
    initializer: Super,
    block: AST.BlockExpression,
    context: LocalContext,
    primaryAssignments: Int = 0
  ): Unit = {
    constructor.superInitializer = initializer
    val body = translate(block, context)
    // A constructor has no `requires` or `ensures`, but the class's `invariant` must hold once the object
    // is built (RFC #2557, design 7.4). It is checked after the body, so a body with a `return` of its own
    // is not guarded: that path would leave without the check, and a check that silently does not run is
    // worse than none. It is named by the warning W0026 instead.
    val checked = constructor.classType match {
      case d: ClassDefinition if bodyContext.contractMode == ContractMode.Runtime && d.invariantSources.nonEmpty =>
        if (containsReturn(block)) {
          bodyContext.warningReporter.report(WarningCategory.ContractNotEnforced, block.location,
            s"the invariant of '${d.name}' is not checked when the object is built: the constructor has a return of its own")
          body
        } else {
          val guards = d.invariants.zip(d.invariantSources).map { case (t, s) =>
            invariantGuard(d, "<init>", t, s, "invariant (after construction)")
          }
          new StatementBlock(body.location, (body +: guards)*)
        }
      case _ => body
    }
    constructor.block = addReturnNode(withPrologue(context, checked), BasicType.VOID)
    constructor.frame = context.getContextFrame
    constructor.primaryAssignments = primaryAssignments
  }

  /** Whether a `return` appears anywhere in the body, in a nested statement or not. */
  private def containsReturn(block: AST.BlockExpression): Boolean = {
    var found = false
    def visit(n: AST.Node): Unit = {
      n match {
        case _: AST.ReturnExpression => found = true
        case _ =>
      }
      if (!found) ASTWalk.visitChildren(n)(visit)
    }
    visit(block)
    found
  }

  /** `if !(clause) throw new ContractViolation("Class.method() <what> failed: invariant <clause>")`. */
  private def invariantGuard(classType: ClassType, methodName: String, clause: Term, source: AST.Expression, what: String): ActionStatement = {
    val strings = bodyContext.load("java.lang.String")
    val violation = bodyContext.load("onion.ContractViolation")
    val ctor = violation.findConstructor(Array[Term](new StringValue(null, "", strings)))
    val message = s"${classType.name}.$methodName() $what failed: invariant ${onion.compiler.contracts.ExpressionText.of(source)}"
    val fail = new Throw(clause.location, new NewObject(clause.location, ctor(0), Array[Term](new StringValue(clause.location, message, strings))))
    new IfStatement(clause.location, new UnaryTerm(clause.location, UnaryTerm.Kind.NOT, BasicType.BOOLEAN, clause), fail, null)
  }

  def reportConstructorOnly(context: LocalContext): Unit =
    reportUnused(context)

  def processExtensionMethodBody(
    extMethod: ExtensionMethodDefinition,
    staticMethod: MethodDefinition,
    node: AST.MethodDeclaration,
    receiverType: Type
  ): Unit = {
    val context = prepareExtensionContext(staticMethod, node, receiverType)
    // An extension method's clauses see the receiver as `this` and the parameters, as a
    // class method's do (#2577); before, they were parsed and then dropped.
    if (node.requires.nonEmpty) staticMethod.preconditions = typeContractClauses(node.requires, context, ContractScope.Pre)
    val triesBefore = bodyContext.tryCount
    val extTranslated = translate(node.block, context)
    staticMethod.mayContainTry = bodyContext.tryCount != triesBefore
    checkMissingReturn(staticMethod.returnType, extTranslated, staticMethod.name, node.block)
    if (node.ensures.nonEmpty) staticMethod.postconditions = typePostconditions(staticMethod, node.ensures, context)
    val translatedBlock = addReturnNode(withPrologue(context, extTranslated), staticMethod.returnType)
    staticMethod.setBlock(translatedBlock)
    staticMethod.setFrame(context.getContextFrame)
    extMethod.setBlock(translatedBlock)
    extMethod.setFrame(context.getContextFrame)
    reportUnused(context)
  }

  def processExtensionMethodDeclaration(
    node: AST.MethodDeclaration,
    receiverType: Type,
    definition: ClassDefinition
  ): Unit =
    if node.block != null then
      typing.kernelNodeOf[ExtensionMethodDefinition](node).foreach { extMethod =>
        val methodTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
        typing.openTypeParams(unitContext.currentTypeParams ++ methodTypeParams) {
          val staticMethod = findStaticExtensionMethod(definition, node, extMethod, receiverType)
          if staticMethod != null then
            processExtensionMethodBody(extMethod, staticMethod, node, receiverType)
        }
      }

  private def prepareMethodContext(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression
  ): LocalContext = {
    val context = new LocalContext
    if (method.modifier & AST.M_STATIC) != 0 then
      context.setStatic(true)
    context.setMethod(method)
    val arguments = method.arguments
    markCapturedVariables(context, args, block)
    bindParameters(context, args, arguments, block)
    predefinedDefaults.get(method) match
      case Some((withDefaults, used)) =>
        used.foreach(context.recordUsage)
        method.setArgumentsWithDefaults(withDefaults)
      case None =>
        method.setArgumentsWithDefaults(
          buildArgumentsWithDefaults(args, arguments, context.recordUsage, DefaultOwner(method.name, method.modifier, method.typeParameters)))
    context
  }

  /** Methods whose defaults were typed ahead of the bodies, with the parameter names those defaults read. */
  private val predefinedDefaults = scala.collection.mutable.HashMap.empty[MethodDefinition, (Array[TypedAST.MethodArgument], Set[String])]

  /**
   * Types a method's defaults before any body of its class is typed, so a call written above the
   * method (`def run() = g()` ... `def g(n: Int = 5)`) already sees them (#2637).
   */
  def predefineDefaults(method: MethodDefinition, args: List[AST.Argument]): Unit =
    if args.exists(_.defaultValue != null) && !predefinedDefaults.contains(method) then
      val used = scala.collection.mutable.Set.empty[String]
      val withDefaults = buildArgumentsWithDefaults(args, method.arguments, used += _, DefaultOwner(method.name, method.modifier, method.typeParameters))
      method.setArgumentsWithDefaults(withDefaults)
      predefinedDefaults(method) = (withDefaults, used.toSet)

  private val predefinedConstructorDefaults = scala.collection.mutable.HashMap.empty[ConstructorDefinition, (Array[TypedAST.MethodArgument], Set[String])]

  private def constructorDefaultOwner(constructor: ConstructorDefinition): DefaultOwner =
    DefaultOwner("init", constructor.modifier | AST.M_STATIC, Array())

  /**
   * Types a constructor's defaults before any body of its class is typed, so a delegation
   * written above it (`def this() : this(z = 1)` with the primary `(z: Int, w: Int = 2)`)
   * already sees them, as [[predefineDefaults]] does for methods.
   */
  def predefineConstructorDefaults(constructor: ConstructorDefinition, args: List[AST.Argument]): Unit =
    if args.exists(_.defaultValue != null) && !predefinedConstructorDefaults.contains(constructor) then
      val used = scala.collection.mutable.Set.empty[String]
      val withDefaults = buildArgumentsWithDefaults(args, constructor.getArgs, used += _, constructorDefaultOwner(constructor))
      constructor.setArgumentsWithDefaults(withDefaults)
      predefinedConstructorDefaults(constructor) = (withDefaults, used.toSet)

  private def prepareExtensionContext(
    staticMethod: MethodDefinition,
    node: AST.MethodDeclaration,
    receiverType: Type
  ): LocalContext = {
    val context = new LocalContext
    context.setStatic(true)
    context.setMethod(staticMethod)

    val extArgs = receiverArgument(node, receiverType) :: node.args
    markCapturedVariables(context, extArgs, node.block)
    // `this`/`self` can never be reassigned, so bind it immutable like any val --
    // the 2-arg `add` defaults to isMutable = true, which would otherwise route a
    // null check on a nullable `self` (extension T? { ... }) into the mutable-var
    // narrowing path, whose flow-sensitive result the guard-clause early-return
    // narrowing in BlockElementLowering never consults.
    context.add("this", receiverType, isMutable = false)

    val arguments = staticMethod.arguments
    val assigned = if (node.block == null) node.args.map(_.name).toSet else bodyContext.assignedNames(node.block)
    context.setReassignedNames(assigned)
    val shared = CapturedVariableScanner.sharedParameters(node.args.map(_.name), node.block, assigned)
    context.markAsBoxed(shared)
    for ((arg, i) <- node.args.zipWithIndex) {
      if shared.contains(arg.name) then context.add(arg.name + "#arg", arguments(i + 1), isMutable = false)
      else context.add(arg.name, arguments(i + 1), isMutable = assigned.contains(arg.name))
    }
    addSharedParameterCopies(context, node.args, arguments.tail, shared)

    staticMethod.setArgumentsWithDefaults(
      buildArgumentsWithDefaults(extArgs, arguments, context.recordUsage, DefaultOwner(staticMethod.name, staticMethod.modifier, staticMethod.typeParameters, staticMethod.classType.asInstanceOf[ClassDefinition])))
    context
  }

  private def findStaticExtensionMethod(
    definition: ClassDefinition,
    node: AST.MethodDeclaration,
    extMethod: ExtensionMethodDefinition,
    receiverType: Type
  ): MethodDefinition =
    definition
      .methods(node.name)
      .find { method =>
        (method.modifier & AST.M_STATIC) != 0 &&
        method.arguments.length == extMethod.arguments.length + 1 &&
        method.arguments(0) == receiverType &&
        extMethod.arguments.indices.forall(i => method.arguments(i + 1) == extMethod.arguments(i))
      }
      .map(_.asInstanceOf[MethodDefinition])
      .orNull

  private def receiverArgument(node: AST.MethodDeclaration, receiverType: Type): AST.Argument =
    AST.Argument(
      node.location,
      "this",
      AST.TypeNode(node.location, AST.ReferenceType(receiverType.name, true), false)
    )

  private def bindParameters(context: LocalContext, args: List[AST.Argument], types: Array[Type], body: AST.Node): Unit = {
    // Parameters never assigned in the body behave like vals, so smart
    // casts (is / null checks) can narrow them
    val assigned =
      if (body == null) args.map(_.name).toSet
      else bodyContext.assignedNames(body)
    // Share the reassigned-name set with local `var` declarations so a var
    // never reassigned in this body can be smart-cast (issue #273).
    context.setReassignedNames(assigned)
    val shared = CapturedVariableScanner.sharedParameters(args.map(_.name), body, assigned)
    context.markAsBoxed(shared)
    var i = 0
    args.foreach { arg =>
      addParameter(context, arg, types(i), assigned, shared)
      i += 1
    }
    addSharedParameterCopies(context, args, types, shared)
  }

  /**
   * Parameters that a closure captures while the body also assigns them. The closure and the
   * method must see one variable, so these live in a box (issue #2335), like a captured `var`.
   */
  private def addParameter(
    context: LocalContext,
    arg: AST.Argument,
    tp: Type,
    assigned: Set[String],
    shared: Set[String]
  ): Unit =
    if shared.contains(arg.name) then
      // The argument slot stays under a name no source can spell; the boxed copy below takes the real name.
      context.add(arg.name + "#arg", tp, isMutable = false)
    else
      context.add(arg.name, tp, isMutable = assigned.contains(arg.name))
    context.recordDeclaration(arg.name, arg.location, isParameter = true)

  /** Declares the boxed local for each shared parameter and records the statements that fill it. */
  private def addSharedParameterCopies(
    context: LocalContext,
    args: List[AST.Argument],
    types: Array[Type],
    shared: Set[String]
  ): Unit =
    args.zipWithIndex.foreach { case (arg, i) =>
      if shared.contains(arg.name) then
        val from = context.lookupOnlyCurrentScope(arg.name + "#arg")
        val index = context.add(arg.name, types(i), isMutable = true)
        context.addParameterPrologue(
          new ExpressionActionStatement(
            new SetLocal(arg.location, 0, index, types(i), new RefLocal(arg.location, 0, from.index, types(i)))))
    }

  private def withPrologue(context: LocalContext, body: ActionStatement): ActionStatement =
    val prologue = context.parameterPrologue
    if prologue.isEmpty then body else new StatementBlock(prologue :+ body*)

  private def markCapturedVariables(context: LocalContext, args: List[AST.Argument], block: AST.BlockExpression): Unit = {
    val paramNames = args.map(_.name).toSet
    val capturedVars = CapturedVariableScanner.scan(block, paramNames)
    context.markAsBoxed(capturedVars)
  }

  /** What a default argument's synthesized method copies from its callee: the name it is derived from, the modifiers (access and static-ness), and the method's type parameters. */
  private final case class DefaultOwner(name: String, modifier: Int, typeParameters: Array[TypeParameter], cls: ClassDefinition = null)

  /**
   * The callee's arguments, each with its default, if any. A default is not spliced into
   * the call site, where `this` is the caller and the callee's parameters do not exist
   * (#2358, #2375): it becomes the body of a method of the callee's own class that takes
   * the parameters before it, so it is typed exactly as the callee's body would see it,
   * minus the parameters not yet bound. A call that omits the argument calls that method
   * on the same receiver (see `DefaultArgument`). A constructor's defaults are static,
   * since no object exists yet.
   */
  private def buildArgumentsWithDefaults(
    args: List[AST.Argument],
    types: Array[Type],
    recordUsage: String => Unit,
    owner: DefaultOwner
  ): Array[TypedAST.MethodArgument] =
    args.zipWithIndex.map { case (arg, i) =>
      // A default is checked like `val x: T = e` (issue #2541): an out-of-range `200` for a
      // `Byte` or a `3.7` for a `Short` is E0000 here, not a truncated value or a VerifyError.
      val defaultTerm = Option(arg.defaultValue).flatMap { expr =>
        synthesizeDefault(args, types, recordUsage, owner, i, expr)
      }
      TypedAST.MethodArgument(arg.name, types(i), defaultTerm)
    }.toArray

  private def synthesizeDefault(
    args: List[AST.Argument],
    types: Array[Type],
    recordUsage: String => Unit,
    owner: DefaultOwner,
    i: Int,
    expr: AST.Expression
  ): Option[Term] = {
    val definition = if owner.cls != null then owner.cls else bodyContext.definition
    val isStatic = (owner.modifier & AST.M_STATIC) != 0
    val access = owner.modifier & (AST.M_PUBLIC | AST.M_PROTECTED | AST.M_PRIVATE)
    val prefixTypes = types.take(i)
    // The declaring class is part of the name, so no subclass method ever overrides a default
    // (#2548): a call uses the default of the method it resolves to at compile time.
    val classTag = definition.name.replace('.', '$')
    val name = defaultMethodName(definition, s"${owner.name}$$default$$${i + 1}$$$classTag", prefixTypes)
    val method = new MethodDefinition(
      expr.location, access | (if isStatic then AST.M_STATIC else 0), definition, name,
      prefixTypes, types(i), null, owner.typeParameters)
    val context = new LocalContext
    if isStatic then context.setStatic(true)
    context.setMethod(method)
    context.setReassignedNames(Set.empty)
    args.take(i).zip(prefixTypes).foreach { (a, t) => context.add(a.name, t, isMutable = false) }
    context.laterParameterNames = args.drop(i).map(_.name).toSet
    val triesBefore = bodyContext.tryCount
    val problemsBefore = bodyContext.problemCount
    val value = typed(expr, context, types(i)).map(term => processAssignable(expr, types(i), term)).filter(_ != null)
    // A parameter read only by a later default is used (no W0006).
    context.usedNames.foreach(recordUsage)
    value match
      case Some(term) =>
        method.mayContainTry = bodyContext.tryCount != triesBefore
        method.setBlock(new StatementBlock(expr.location, new Return(expr.location, term)))
        method.setFrame(context.getContextFrame)
        definition.add(method)
        Some(new DefaultArgument(expr.location, types(i), method, i))
      case None if bodyContext.problemCount != problemsBefore =>
        // The default's own error is reported; still count the parameter as defaulted, so
        // each call that omits it is not also rejected with E0005 (it never reaches codegen).
        Some(new DefaultArgument(expr.location, types(i), method, i))
      case None => None
  }

  /** `base`, or `base$2`, `base$3`, ... when an overload already took `base` for the same parameter types. */
  private def defaultMethodName(definition: ClassDefinition, base: String, params: Array[Type]): String = {
    def taken(n: String) = definition.methods(n).exists(_.arguments.sameElements(params))
    if !taken(base) then base
    else Iterator.from(2).map(k => s"$base$$$k").find(n => !taken(n)).get
  }

  private def reportUnused(context: LocalContext): Unit =
    typing.reportUnusedVariables(context)

  private def termTypes(terms: Array[Term]): Array[Type] =
    if terms == null then Array.empty else terms.map(_.`type`)
}
