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
      definition.primaryParameterNames = (node.defaultSection ++ node.sections).flatMap(_.members).collect {
        case ctor: AST.ConstructorDeclaration if ctor.primary => ctor.args.map(_.name)
      }.flatten.toSet
      // Most defaults were typed ahead by predefineUnitDefaults; one that reads a top-level
      // `val`/`var` waits until here, so a call above it in this class still sees it (#2637).
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
   * Types the defaults of every method and constructor in this unit, before any body of any unit
   * is typed (#2653). A call can then omit a default declared later in the file, in another type,
   * or in another file, as #2637 already allowed within one class body.
   */
  def predefineUnitDefaults(unit: AST.CompilationUnit): Unit = {
    val topLevelVariables = unit.toplevels.collect {
      case v: AST.LocalVariableDeclaration => v.name
      case v: AST.GlobalVariableDeclaration => v.name
    }.toSet
    // A top-level `val`/`var` becomes a field only when the body pass reaches it, so a default
    // that reads one, in any kind of declaration, is still typed in source order, after it.
    def readsTopLevel(args: List[AST.Argument]): Boolean = readsAny(args, topLevelVariables)
    for (toplevel <- unit.toplevels) toplevel match {
      case node: AST.ClassDeclaration =>
        inDefinition(node, typing.declaredTypeParams_.getOrElse(node, Seq())) { _ =>
          for (section <- node.defaultSection ++ node.sections; member <- section.members) member match {
            case m: AST.MethodDeclaration if !readsTopLevel(m.args) => predefineMethodDefaults(m)
            case c: AST.ConstructorDeclaration if !readsTopLevel(c.args) =>
              typing.kernelNodeOf[ConstructorDefinition](c).foreach(methodBodySupport.predefineConstructorDefaults(_, c.args))
            case _ =>
          }
        }
      case node: AST.RecordDeclaration =>
        inDefinition(node, typing.declaredTypeParams_.getOrElse(node, Seq())) { _ =>
          node.synthesizedMethods.filterNot(m => readsTopLevel(m.args)).foreach(predefineMethodDefaults)
          for (section <- node.sections; member <- section.members) member match {
            case m: AST.MethodDeclaration if !readsTopLevel(m.args) => predefineMethodDefaults(m)
            case _ =>
          }
        }
      case node: AST.EnumDeclaration =>
        inDefinition(node, Seq()) { _ =>
          for (section <- node.sections; member <- section.members) member match {
            case m: AST.MethodDeclaration if !readsTopLevel(m.args) => predefineMethodDefaults(m)
            case _ =>
          }
        }
      case node: AST.InterfaceDeclaration =>
        inDefinition(node, typing.declaredTypeParams_.getOrElse(node, Seq())) { _ =>
          node.methods.filterNot(m => readsTopLevel(m.args)).foreach(predefineMethodDefaults)
        }
      case node: AST.ExtensionDeclaration =>
        typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
          unitContext.currentDefinition = definition
          typing.find(definition.name).foreach(unitContext.currentMapper = _)
          typing.mapFromExtensionReceiver(node.receiverType).foreach { receiverType =>
            node.methods.filterNot(m => readsTopLevel(m.args)).foreach(methodBodySupport.predefineExtensionDefaults(_, receiverType, definition))
          }
        }
      case node: AST.FunctionDeclaration if node.block != null && !readsTopLevel(node.args) =>
        typing.kernelNodeOf[MethodDefinition](node).foreach { function =>
          typing.loadTopClass.collect { case cd: ClassDefinition => cd }.foreach(unitContext.currentDefinition = _)
          typing.find(typing.topClass).foreach(unitContext.currentMapper = _)
          typing.openTypeParams(typing.emptyTypeParams ++ typing.declaredTypeParams_.getOrElse(node, Seq())) {
            methodBodySupport.predefineDefaults(function, node.args)
          }
        }
      case _ =>
    }
    unitContext.currentDefinition = null
    unitContext.currentMapper = null
  }

  /**
   * Whether any default mentions one of `names`, as a variable or as a bare call. The scan walks
   * every field of every node, closures and trailing lambdas included, so no node kind can hide
   * a read (panel round 2 on #2798). A false positive only keeps a default in source order.
   */
  private def readsAny(args: List[AST.Argument], names: Set[String]): Boolean = names.nonEmpty && {
    var found = false
    def visit(x: Any): Unit = if (!found) x match {
      case id: AST.Id if names.contains(id.name) => found = true
      case call: AST.UnqualifiedMethodCall if names.contains(call.name) => found = true
      case _: Location => ()
      case xs: IterableOnce[?] => xs.iterator.foreach(visit)
      case p: Product => p.productIterator.foreach(visit)
      case _ => ()
    }
    args.foreach(a => if (a.defaultValue != null) visit(a.defaultValue))
    found
  }

  private def inDefinition(node: AST.TypeDeclaration, typeParams: Seq[TypeParam])(body: ClassDefinition => Unit): Unit =
    typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
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
