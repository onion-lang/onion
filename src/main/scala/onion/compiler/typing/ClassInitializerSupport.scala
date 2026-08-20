package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import scala.collection.mutable.Buffer

private[compiler] final class ClassInitializerSupport(
  typing: Typing,
  typed: (AST.Expression, LocalContext, Type) => Option[Term],
  processAssignable: (AST.Node, Type, Term) => Term
) {
  def collectFieldInitializer(
    node: AST.FieldDeclaration,
    definition: ClassDefinition,
    instanceInitializers: Buffer[ActionStatement],
    staticInitializers: Buffer[ActionStatement]
  ): Unit =
    typing.kernelNodeOf[FieldDefinition](node).foreach { field =>
      collectInitializer(node.modifiers, node.init, field, definition, instanceInitializers, staticInitializers)
    }

  def collectDelegatedFieldInitializer(
    node: AST.DelegatedFieldDeclaration,
    definition: ClassDefinition,
    instanceInitializers: Buffer[ActionStatement],
    staticInitializers: Buffer[ActionStatement]
  ): Unit =
    typing.kernelNodeOf[FieldDefinition](node).foreach { field =>
      collectInitializer(node.modifiers, node.init, field, definition, instanceInitializers, staticInitializers)
    }

  def injectInstanceInitializers(classDef: ClassDefinition, initializers: Seq[ActionStatement]): Unit = {
    if initializers.isEmpty then return
    classDef.constructors.foreach {
      // A constructor that delegates to a sibling via `: this(...)` must NOT run
      // instance field initializers itself; the ultimately-invoked constructor
      // (the one calling super) runs them. Otherwise fields double-initialize.
      case ctor: ConstructorDefinition if ctor.superInitializer != null && ctor.superInitializer.selfDelegation => ()
      case ctor: ConstructorDefinition =>
        val existing = Option(ctor.block).map(_.statements.toIndexedSeq).getOrElse(Seq.empty)
        // The primary constructor's own `this.x = x` assignments come first, then the
        // declared field initializers, then anything else. A field initializer such as
        // `var total: Int = x * 2` names the *field* `x` (it is typed without the
        // constructor's parameters in scope), so it has to run after that field was
        // stored -- put the initializers first and `total` is always 0. This is the
        // order Kotlin and Scala use, and it is what a reader of the class expects.
        //
        // The typed primary body has the shape StatementBlock(StatementBlock(assign*),
        // Return): translate() lowers the parser's assignment-only block to the inner
        // StatementBlock and addReturnNode() wraps it. So the assignments are the whole
        // first statement, and the initializers slot in right after it.
        val combined =
          if ctor.primaryAssignments > 0 && existing.nonEmpty then
            (existing.take(1) ++ initializers ++ existing.drop(1)).toArray
          else (initializers ++ existing).toArray
        ctor.block = new StatementBlock(combined: _*)
      case _ => ()
    }
  }

  private def collectInitializer(
    modifiers: Int,
    init: AST.Expression,
    field: FieldDefinition,
    definition: ClassDefinition,
    instanceInitializers: Buffer[ActionStatement],
    staticInitializers: Buffer[ActionStatement]
  ): Unit = {
    if init == null then return
    val context = new LocalContext
    val isStatic = Modifier.isStatic(modifiers)
    context.setStatic(isStatic)
    val fieldType = field.`type`
    typed(init, context, fieldType) match {
      case Some(term) =>
        val value = processAssignable(init, fieldType, term)
        if value != null then
          val statement =
            if isStatic then
              new ExpressionActionStatement(new SetStaticField(definition, field, value))
            else
              new ExpressionActionStatement(new SetField(new This(definition), field, value))
          if isStatic then staticInitializers += statement else instanceInitializers += statement
      case None => ()
    }
  }
}
