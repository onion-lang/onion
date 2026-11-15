package onion.compiler.typing

import onion.compiler.AST

/**
 * The declared name of a superclass or super-interface, as the contract passes walk a hierarchy
 * in the untyped tree. A generic supertype (`extends Base[String]`, `conforms I[T]`) is a
 * `ParameterizedType` around the name; it names the same declaration as `extends Base`, so
 * its mutators and clauses belong to the walk as well (#2888).
 */
private[typing] object ContractSupertypes {
  def name(t: AST.TypeNode): Option[String] = Option(t).map(_.desc).flatMap(nameOf)

  private def nameOf(d: AST.TypeDescriptor): Option[String] = d match {
    case AST.ReferenceType(n, _)        => Some(n)
    case AST.ParameterizedType(base, _) => nameOf(base)
    case _                              => None
  }
}
