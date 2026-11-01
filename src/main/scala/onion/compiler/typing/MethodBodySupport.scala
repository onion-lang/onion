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
  processAssignable: (AST.Node, Type, Term) => Term
) {
  def processMethodLikeBody(
    method: MethodDefinition,
    args: List[AST.Argument],
    block: AST.BlockExpression
  ): Unit = {
    val context = prepareMethodContext(method, args, block)
    val triesBefore = bodyContext.tryCount
    val translated = translate(block, context)
    method.mayContainTry = bodyContext.tryCount != triesBefore
    checkMissingReturn(method.returnType, translated, method.name, block)
    val translatedBlock = addReturnNode(withPrologue(context, translated), method.returnType)
    method.setBlock(translatedBlock)
    method.setFrame(context.getContextFrame)
    reportUnused(context)
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
    constructor.setArgumentsWithDefaults(
      buildArgumentsWithDefaults(args, paramTypes, context, DefaultOwner("init", constructor.modifier | AST.M_STATIC, Array())))
    context
  }

  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit =
    typing.kernelNodeOf[ConstructorDefinition](node).foreach { constructor =>
      val context = prepareConstructorContext(constructor, node.args, node.block)
      val params0 = context.withArgumentSlots(typedTerms(node.superInits.toArray, context))
      // The object does not exist while its super/self initializer arguments are being
      // evaluated. A field read there -- `def this : this(seed)` where `seed` is a field,
      // or an explicit `this` -- is a GETFIELD on `uninitializedThis`, which the JVM
      // verifier rejects; it used to surface as an internal error at class load. Closure
      // bodies are exempt: they capture `this` and run later, when it does exist.
      if params0 != null then
        params0.zip(node.superInits).collectFirst {
          case (term, source) if TermWalk.exists(term) { case _: This => true; case _ => false } => source
        }.foreach(source => bodyContext.report(THIS_BEFORE_CONSTRUCTOR_DELEGATION, source, bodyContext.definition))
      val targetClass = if node.selfDelegation then bodyContext.definition else bodyContext.definition.superClass
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
        val init = new Super(targetClass, matched(0).getArgs, params, node.selfDelegation)
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
        if (!f.isBasicType && p.isBasicType) Boxing.boxing(bodyContext.table, p)
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
    constructor.block = addReturnNode(withPrologue(context, translate(block, context)), BasicType.VOID)
    constructor.frame = context.getContextFrame
    constructor.primaryAssignments = primaryAssignments
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
    val triesBefore = bodyContext.tryCount
    val extTranslated = translate(node.block, context)
    staticMethod.mayContainTry = bodyContext.tryCount != triesBefore
    checkMissingReturn(staticMethod.returnType, extTranslated, staticMethod.name, node.block)
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
    method.setArgumentsWithDefaults(
      buildArgumentsWithDefaults(args, arguments, context, DefaultOwner(method.name, method.modifier, method.typeParameters)))
    context
  }

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
    val shared = sharedParameters(node.args.map(_.name), node.block, assigned)
    context.markAsBoxed(shared)
    for ((arg, i) <- node.args.zipWithIndex) {
      if shared.contains(arg.name) then context.add(arg.name + "#arg", arguments(i + 1), isMutable = false)
      else context.add(arg.name, arguments(i + 1), isMutable = assigned.contains(arg.name))
    }
    addSharedParameterCopies(context, node.args, arguments.tail, shared)

    staticMethod.setArgumentsWithDefaults(
      buildArgumentsWithDefaults(extArgs, arguments, context, DefaultOwner(staticMethod.name, staticMethod.modifier, staticMethod.typeParameters, staticMethod.classType.asInstanceOf[ClassDefinition])))
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
    val shared = sharedParameters(args.map(_.name), body, assigned)
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
  private def sharedParameters(names: List[String], body: AST.Node, assigned: Set[String]): Set[String] =
    body match
      case block: AST.BlockExpression =>
        CapturedVariableScanner.scan(block) & names.toSet & assigned
      case _ => Set.empty

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
    calleeContext: LocalContext,
    owner: DefaultOwner
  ): Array[TypedAST.MethodArgument] =
    args.zipWithIndex.map { case (arg, i) =>
      val defaultTerm = Option(arg.defaultValue).flatMap { expr =>
        synthesizeDefault(args, types, calleeContext, owner, i, expr)
      }
      TypedAST.MethodArgument(arg.name, types(i), defaultTerm)
    }.toArray

  private def synthesizeDefault(
    args: List[AST.Argument],
    types: Array[Type],
    calleeContext: LocalContext,
    owner: DefaultOwner,
    i: Int,
    expr: AST.Expression
  ): Option[Term] = {
    val definition = if owner.cls != null then owner.cls else bodyContext.definition
    val isStatic = (owner.modifier & AST.M_STATIC) != 0
    val access = owner.modifier & (AST.M_PUBLIC | AST.M_PROTECTED | AST.M_PRIVATE)
    val prefixTypes = types.take(i)
    val name = defaultMethodName(definition, s"${owner.name}$$default$$${i + 1}", prefixTypes)
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
    context.usedNames.foreach(calleeContext.recordUsage)
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
