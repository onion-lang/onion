package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.{TypingBodyContext, TypingUnitContext}

import scala.collection.mutable
import scala.collection.mutable.Buffer

private[compiler] final class DeclarationBodySupport(
  typing: Typing,
  unitContext: TypingUnitContext,
  bodyContext: TypingBodyContext,
  classInitializerSupport: ClassInitializerSupport,
  methodBodySupport: MethodBodySupport,
  processMethodDeclaration: AST.MethodDeclaration => Unit,
  processConstructorDeclaration: AST.ConstructorDeclaration => Unit
) {
  def processClassDeclaration(node: AST.ClassDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    typing.find(definition.name).foreach(unitContext.currentMapper = _)
    val classTypeParams = typing.declaredTypeParams_.getOrElse(node, Seq())
    typing.openTypeParams(typing.emptyTypeParams ++ classTypeParams) {
      methodBodySupport.processTypeContracts(definition, node.invariants, node.histories)
      val instanceInitializers = Buffer[ActionStatement]()
      val staticInitializers = Buffer[ActionStatement]()
      recordPrimaryParameterNames(node, definition)
      // Most defaults were typed ahead by DefaultScheduling. A held-back one (it reads a top-level
      // `val`/`var`, or calls a method whose default does) waits until here, so a call above it
      // in this class still sees it (#2637).
      for (section <- node.defaultSection ++ node.sections; member <- section.members) member match {
        case m: AST.MethodDeclaration => predefineMethodDefaults(m)
        case c: AST.ConstructorDeclaration =>
          typing.kernelNodeOf[ConstructorDefinition](c).foreach(methodBodySupport.predefineConstructorDefaults(_, c.args))
        case _ =>
      }
      for (section <- node.defaultSection ++ node.sections; member <- section.members) {
        member match {
          case field: AST.FieldDeclaration =>
            classInitializerSupport.collectFieldInitializer(field, definition, instanceInitializers, staticInitializers)
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            // A class with a primary constructor routes every construction through it: the
            // primary owns the superclass call and stores the `val`/`var` parameter fields.
            // A `def this` that does not delegate would go around it -- reaching the
            // superclass with an implicit `super()` the class never wrote, and leaving the
            // parameter fields at their defaults. Report once and do not type the body:
            // typing it would resolve `super()` against the wrong class and add an E0021
            // that is only a symptom of this one.
            if (node.hasPrimary && !member.primary && !member.selfDelegation)
              bodyContext.report(
                SemanticError.SECONDARY_CONSTRUCTOR_MUST_DELEGATE, member,
                definition, primaryArguments(node))
            else
              processConstructorDeclaration(member)
          case field: AST.DelegatedFieldDeclaration =>
            classInitializerSupport.collectDelegatedFieldInitializer(field, definition, instanceInitializers, staticInitializers)
        }
      }
      reportDelegationCycles(node, definition)
      checkImplicitSuperConstructor(node, definition)
      classInitializerSupport.injectInstanceInitializers(definition, instanceInitializers.toSeq)
      if (staticInitializers.nonEmpty) {
        definition.setStaticInitializers(staticInitializers.toArray)
      }
    }
  }

  /**
   * The defaults of every method and constructor in this unit, one job per declaration, for
   * [[DefaultScheduling]] to type before any body of any unit (#2653). A call can then omit a
   * default declared later in the file, in another type, or in another file, as #2637 already
   * allowed within one class body. A job that is never run is typed in source order instead.
   */
  def unitDefaultJobs(unit: AST.CompilationUnit): Seq[DefaultScheduling.Job] = {
    val topLevelVariables = unit.toplevels.collect {
      case v: AST.LocalVariableDeclaration => v.name
      case v: AST.GlobalVariableDeclaration => v.name
    }.toSet
    def job(name: String, args: List[AST.Argument])(body: => Unit): Option[DefaultScheduling.Job] =
      if (!args.exists(_.defaultValue != null)) None
      else {
        val mentions = mentionedNames(args)
        // A top-level `val`/`var` becomes a field only when the body pass reaches it, so a default
        // that reads one, in any kind of declaration, stays in source order, after it.
        Some(DefaultScheduling.Job(name, mentions - name, (mentions & topLevelVariables).nonEmpty, () => {
          body
          unitContext.currentDefinition = null
          unitContext.currentMapper = null
        }))
      }
    def typeParams(node: AST.Node): Seq[TypeParam] = typing.declaredTypeParams_.getOrElse(node, Seq())
    def methodJobs(node: AST.TypeDeclaration, params: Seq[TypeParam], methods: Seq[AST.MethodDeclaration]) =
      methods.flatMap(m => job(m.name, m.args)(inDefinition(node, params)(_ => predefineMethodDefaults(m))))
    unit.toplevels.flatMap {
      case node: AST.ClassDeclaration =>
        val members = (node.defaultSection ++ node.sections).toSeq.flatMap(_.members)
        methodJobs(node, typeParams(node), members.collect { case m: AST.MethodDeclaration => m }) ++
          members.collect { case c: AST.ConstructorDeclaration => c }.flatMap { c =>
            job(node.name, c.args)(inDefinition(node, typeParams(node)) { _ =>
              typing.kernelNodeOf[ConstructorDefinition](c).foreach(methodBodySupport.predefineConstructorDefaults(_, c.args))
            })
          }
      case node: AST.RecordDeclaration =>
        methodJobs(node, typeParams(node),
          node.synthesizedMethods ++ node.sections.flatMap(_.members).collect { case m: AST.MethodDeclaration => m })
      case node: AST.EnumDeclaration =>
        methodJobs(node, Seq(), node.sections.flatMap(_.members).collect { case m: AST.MethodDeclaration => m })
      case node: AST.InterfaceDeclaration =>
        methodJobs(node, typeParams(node), node.methods)
      case node: AST.ExtensionDeclaration =>
        node.methods.flatMap { m =>
          job(m.name, m.args) {
            typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
              unitContext.currentDefinition = definition
              typing.find(definition.name).foreach(unitContext.currentMapper = _)
              typing.mapFromExtensionReceiver(node.receiverType).foreach { receiverType =>
                methodBodySupport.predefineExtensionDefaults(m, receiverType, definition)
              }
            }
          }
        }
      case node: AST.FunctionDeclaration if node.block != null =>
        job(node.name, node.args) {
          typing.kernelNodeOf[MethodDefinition](node).foreach { function =>
            typing.loadTopClass.collect { case cd: ClassDefinition => cd }.foreach(unitContext.currentDefinition = _)
            typing.find(typing.topClass).foreach(unitContext.currentMapper = _)
            typing.openTypeParams(typing.emptyTypeParams ++ typeParams(node)) {
              methodBodySupport.predefineDefaults(function, node.args)
            }
          }
        }.toSeq
      case _ => Seq()
    }
  }

  /**
   * Every name the defaults mention: any node's `name` (a variable, a call of any form, a
   * member) and every type they name. The scan walks every field of every node, closures and trailing lambdas
   * included, so no node kind can hide a read (panel round 2 on #2798). A false match only
   * keeps a default in source order, as on `main`.
   */
  private def mentionedNames(args: List[AST.Argument]): Set[String] = {
    val names = scala.collection.mutable.Set.empty[String]
    def visit(x: Any): Unit = x match {
      case ref: AST.ReferenceType => names += ref.name.split('.').last
      case _: Location => ()
      case xs: IterableOnce[?] => xs.iterator.foreach(visit)
      // Any node's `name` field counts, so no call form (`super.g()`, a trait call, a member
      // selection, ...) can hide a dependency (panel round 4 on #2798).
      case p: Product =>
        p.productElementNames.zip(p.productIterator).foreach {
          case ("name", name: String) => names += name
          case (_, child) => visit(child)
        }
      case _ => ()
    }
    args.foreach(a => if (a.defaultValue != null) visit(a.defaultValue))
    // A `type` alias names its target too, so `new BB()` through `type BB = B` mentions `B`
    // (panel round 5 on #2798). Expanding until nothing new appears covers alias chains.
    val aliasTargets = typing.typeAliases_.values.toSeq.groupMap(_.node.name)(_.node.targetType)
    val expanded = scala.collection.mutable.Set.empty[String]
    var pending = names.toSet.intersect(aliasTargets.keySet)
    while (pending.nonEmpty) {
      expanded ++= pending
      pending.foreach(alias => aliasTargets(alias).foreach(visit))
      pending = names.toSet.intersect(aliasTargets.keySet) -- expanded
    }
    names.toSet
  }

  /** A plain primary-constructor parameter is not a field; E0002 on one hints so (#2515). */
  private def recordPrimaryParameterNames(node: AST.ClassDeclaration, definition: ClassDefinition): Unit =
    definition.primaryParameterNames = (node.defaultSection ++ node.sections).flatMap(_.members).collect {
      case ctor: AST.ConstructorDeclaration if ctor.primary => ctor.args.map(_.name)
    }.flatten.toSet

  private def inDefinition(node: AST.TypeDeclaration, typeParams: Seq[TypeParam])(body: ClassDefinition => Unit): Unit =
    typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
      // A default typed ahead of the body pass still gets the constructor-parameter hint (panel round 5 on #2798).
      node match {
        case c: AST.ClassDeclaration => recordPrimaryParameterNames(c, definition)
        case _ =>
      }
      unitContext.currentDefinition = definition
      typing.find(definition.name).foreach(unitContext.currentMapper = _)
      typing.openTypeParams(typing.emptyTypeParams ++ typeParams)(body(definition))
    }

  /** An explicitly `abstract` method with a body is an error the body pass reports; its defaults wait for that. */
  private def predefineMethodDefaults(m: AST.MethodDeclaration): Unit =
    if (m.block == null || !AST.hasModifier(m.modifiers, AST.M_ABSTRACT))
      typing.kernelNodeOf[MethodDefinition](m).foreach { method =>
        typing.openTypeParams(unitContext.currentTypeParams ++ typing.declaredTypeParams_.getOrElse(m, Seq())) {
          methodBodySupport.predefineDefaults(method, m.args)
        }
      }

  /** A class that declares no constructor gets one that calls `super()`. When the superclass has
   *  no accessible no-arg constructor, that call used to compile and then throw NoSuchMethodError
   *  on `new C()` (#2664), e.g. when every parameter of the parent's only constructor has a default.
   *  An explicit `def this { }` already reports E0021 for it; the implicit one reports it too. */
  private def checkImplicitSuperConstructor(node: AST.ClassDeclaration, definition: ClassDefinition): Unit = {
    val declaresConstructor = (node.defaultSection ++ node.sections).exists(_.members.exists(_.isInstanceOf[AST.ConstructorDeclaration]))
    val parent = definition.superClass
    if (!declaresConstructor && parent != null) {
      val matched = parent.findConstructor(Array.empty[Term])
      if (matched.length == 0)
        bodyContext.report(SemanticError.CONSTRUCTOR_NOT_FOUND, node, parent, Array.empty[Type], parent.constructors)
      else if (matched.length == 1 && !MemberAccess.isMemberAccessible(matched(0), definition))
        bodyContext.report(SemanticError.METHOD_NOT_ACCESSIBLE, node, matched(0).affiliation, "new", matched(0).getArgs, definition)
    }
  }

  /** The primary constructor's parameter types, for the E0087 message's `: this(...)`. */
  private def primaryArguments(node: AST.ClassDeclaration): Array[Type] =
    (node.defaultSection ++ node.sections).flatMap(_.members).collectFirst {
      case ctor: AST.ConstructorDeclaration if ctor.primary =>
        typing.kernelNodeOf[ConstructorDefinition](ctor).map(_.getArgs).getOrElse(Array.empty[Type])
    }.getOrElse(Array.empty[Type])

  /**
   * `def this(a) : this(b)` with `def this(b) : this(a)` is a StackOverflowError at `new`,
   * and nothing used to say so. Every self-delegating constructor has exactly one outgoing
   * edge -- the sibling its arguments matched -- so a walk from each constructor either
   * reaches one that calls the superclass or revisits a constructor already on the current
   * path. Reported at most once per cycle, on the first constructor found to be on one.
   *
   * The edge is recovered by argument types rather than stored, because a `Super` only
   * records the types the sibling was matched with; comparing those to each sibling's own
   * parameter types is the same comparison `findConstructor` made when it resolved them.
   */
  private def reportDelegationCycles(node: AST.ClassDeclaration, definition: ClassDefinition): Unit = {
    val ctors = definition.constructors.collect { case c: ConstructorDefinition => c }
    // A cycle needs a self-delegating constructor; without one there is nothing to walk.
    if (!ctors.exists(c => c.superInitializer != null && c.superInitializer.selfDelegation)) return
    def target(c: ConstructorDefinition): Option[ConstructorDefinition] =
      Option(c.superInitializer).filter(_.selfDelegation).flatMap { init =>
        ctors.find(sibling => sibling.getArgs.toSeq == init.arguments.toSeq)
      }
    val cleared = mutable.Set[ConstructorDefinition]()
    val declared = (node.defaultSection ++ node.sections).flatMap(_.members)
      .collect { case c: AST.ConstructorDeclaration => c }
    ctors.foreach { start =>
      val path = mutable.LinkedHashSet[ConstructorDefinition]()
      var current: Option[ConstructorDefinition] = Some(start)
      while (current.exists(c => !cleared.contains(c) && !path.contains(c))) {
        path += current.get
        current = target(current.get)
      }
      current match {
        case Some(onCycle) if path.contains(onCycle) =>
          // Anchor the diagnostic on the declaration whose signature matches, so the
          // caret lands on a `def this` line rather than on the class.
          val decl = declared.find(d => typing.kernelNodeOf[ConstructorDefinition](d).contains(onCycle)).getOrElse(node)
          bodyContext.report(
            SemanticError.CONSTRUCTOR_DELEGATION_CYCLE, decl,
            definition, onCycle.getArgs)
          path.foreach(cleared += _)
        case _ =>
          path.foreach(cleared += _)
      }
    }
  }

  def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    typing.find(definition.name).foreach(unitContext.currentMapper = _)

    typing.mapFromExtensionReceiver(node.receiverType).foreach { receiverType =>
      for (methodNode <- node.methods) {
        methodBodySupport.processExtensionMethodDeclaration(methodNode, receiverType, definition)
      }
    }
  }
}
