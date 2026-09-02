package onion.compiler.typing.session

import onion.compiler.AST
import onion.compiler.TypedAST

import scala.jdk.CollectionConverters.*

final class AstBindingIndex {
  // Keyed by identity. AST nodes are case classes, so a structural HashMap hashes the
  // whole subtree under a node (and compares it on collision) at every bind -- for a
  // class declaration that is the entire class body, per binding. The binding is a
  // relation between *these* node objects, not between shapes: the typer binds the
  // node it just visited, and the REPL and debug consumers look up the very instances
  // they were handed. Two equal-shaped nodes at different positions were never meant to
  // share a binding, and their Locations differ anyway.
  // Presized: a few thousand bindings per unit is ordinary, and IdentityHashMap doubles
  // by copying, which showed up as resize() in profiles when it started at the default 32.
  private val astToTypedJ = new java.util.IdentityHashMap[AST.Node, TypedAST.Node](1024)
  private val typedToAstJ = new java.util.IdentityHashMap[TypedAST.Node, AST.Node](1024)
  private val astToTyped: scala.collection.mutable.Map[AST.Node, TypedAST.Node] = astToTypedJ.asScala
  private val typedToAst: scala.collection.mutable.Map[TypedAST.Node, AST.Node] = typedToAstJ.asScala

  def bind(ast: AST.Node, typed: TypedAST.Node): Unit = {
    astToTyped(ast) = typed
    typedToAst(typed) = ast
  }

  def typedOf(ast: AST.Node): Option[TypedAST.Node] =
    astToTyped.get(ast)

  def astOf(typed: TypedAST.Node): Option[AST.Node] =
    typedToAst.get(typed)

  /**
   * A read-only, identity-keyed view for debug artifacts and the REPL, which look up node
   * instances they hold. This used to be `astToTyped.toMap`, which built an immutable
   * HashMap -- and hashing every AST node structurally (each node's hash walks its whole
   * subtree) for a map the ordinary compile never reads was one of the larger costs left
   * in the type checker. The view copies nothing; `updated`/`removed` are honoured by
   * copying, which nothing on the hot path does.
   */
  def allTypedBindings: Map[AST.Node, TypedAST.Node] =
    // The live map, read-only through the facade: it is only consulted after the
    // compilation that filled it has finished, so a defensive copy bought nothing and cost
    // a full pass over every binding per compile.
    new AstBindingIndex.IdentityMapView(astToTypedJ)
}

object AstBindingIndex {
  /** An immutable-Map facade over an identity-keyed java map. Lookups are by identity. */
  final class IdentityMapView[K <: AnyRef, V](underlying: java.util.IdentityHashMap[K, V]) extends scala.collection.immutable.AbstractMap[K, V] {
    override def get(key: K): Option[V] = Option(underlying.get(key))
    override def iterator: Iterator[(K, V)] = underlying.entrySet().asScala.iterator.map(e => (e.getKey, e.getValue))
    override def size: Int = underlying.size
    override def contains(key: K): Boolean = underlying.containsKey(key)
    override def updated[V1 >: V](key: K, value: V1): scala.collection.immutable.Map[K, V1] = {
      val copy = new java.util.IdentityHashMap[K, V1](underlying.asInstanceOf[java.util.Map[K, V1]])
      copy.put(key, value); new IdentityMapView(copy)
    }
    override def removed(key: K): scala.collection.immutable.Map[K, V] = {
      val copy = new java.util.IdentityHashMap[K, V](underlying); copy.remove(key); new IdentityMapView(copy)
    }
  }
}
