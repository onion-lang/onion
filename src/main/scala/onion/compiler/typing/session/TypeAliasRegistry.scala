package onion.compiler.typing.session

import onion.compiler.typing.TypeAliasEntry

import scala.collection.mutable.{HashMap, Set => MutableSet}

final class TypeAliasRegistry {
  val entries: HashMap[String, TypeAliasEntry] = HashMap()
  val resolutionStack: MutableSet[String] = MutableSet()
}
