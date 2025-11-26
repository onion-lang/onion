package onion.compiler.generics

import onion.compiler.AST

/**
  * Minimal data model for generics plumbing (parser → typing → codegen).
  * Not yet wired into the existing pipeline; serves as a staging area to avoid churn.
  */
object GenericModel {
  /** A type parameter with an optional upper bound. */
  final case class TypeParameter(name: String, upperBound: Option[AST.TypeNode])

  /** A type application like Foo[Bar, Baz]. */
  final case class TypeApplication(target: AST.TypeNode, arguments: List[AST.TypeNode])
}

