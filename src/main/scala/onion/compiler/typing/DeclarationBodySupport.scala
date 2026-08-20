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
      val instanceInitializers = Buffer[ActionStatement]()
      val staticInitializers = Buffer[ActionStatement]()
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
      classInitializerSupport.injectInstanceInitializers(definition, instanceInitializers.toSeq)
      if (staticInitializers.nonEmpty) {
        definition.setStaticInitializers(staticInitializers.toArray)
      }
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

    typing.mapFrom(node.receiverType).foreach { receiverType =>
      for (methodNode <- node.methods) {
        methodBodySupport.processExtensionMethodDeclaration(methodNode, receiverType, definition)
      }
    }
  }
}
