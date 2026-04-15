package onion.compiler.typing.session

import onion.compiler.AST
import onion.compiler.TypedAST

import scala.collection.mutable.HashMap

final class AstBindingIndex {
  private val astToTyped = HashMap[AST.Node, TypedAST.Node]()
  private val typedToAst = HashMap[TypedAST.Node, AST.Node]()

  def bind(ast: AST.Node, typed: TypedAST.Node): Unit = {
    astToTyped(ast) = typed
    typedToAst(typed) = ast
  }

  def typedOf(ast: AST.Node): Option[TypedAST.Node] =
    astToTyped.get(ast)

  def astOf(typed: TypedAST.Node): Option[AST.Node] =
    typedToAst.get(typed)

  def allTypedBindings: Map[AST.Node, TypedAST.Node] =
    astToTyped.toMap
}
