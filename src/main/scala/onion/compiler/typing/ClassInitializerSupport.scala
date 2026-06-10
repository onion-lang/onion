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
      case ctor: ConstructorDefinition =>
        val existing = Option(ctor.block).map(_.statements.toIndexedSeq).getOrElse(Seq.empty)
        val combined = (initializers ++ existing).toArray
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
