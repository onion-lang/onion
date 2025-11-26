package onion.compiler.generics

import onion.compiler.{TypedAST, IRT}

/**
  * Placeholder erasure helper. Will be expanded when generics are fully threaded through typing.
  */
object Erasure {
  /**
    * Compute the erased JVM type for a type parameter. For now, falls back to `Object`.
    * When bounds are available, this should erase to the upper bound’s erasure.
    */
  def erasedTypeParam(bound: Option[TypedAST.Type]): TypedAST.Type =
    bound.getOrElse(TypedAST.BasicType.VOID)
}
