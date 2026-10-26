package onion.compiler.typing

import onion.compiler.{AST, Location}

/** Every name a piece of source could refer to: each identifier, and every other string in
 *  its nodes (a called name, a field name), which over-approximates the locals it reads.
 *  Memo keys use it to leave out variables a lambda never mentions (#2452). */
private[compiler] object MentionedNames {
  private val memo = new java.util.IdentityHashMap[AnyRef, Set[String]]()

  def of(node: AnyRef): Set[String] = memo.synchronized {
    val cached = memo.get(node)
    if (cached != null) cached
    else {
      val names = scala.collection.mutable.HashSet[String]()
      collect(node, names)
      val result = names.toSet
      memo.put(node, result)
      result
    }
  }

  private def collect(value: Any, into: scala.collection.mutable.HashSet[String]): Unit = value match {
    case _: Location => ()
    case s: String => into += s
    case p: Product => p.productIterator.foreach(collect(_, into))
    case xs: Iterable[?] => xs.foreach(collect(_, into))
    case xs: Array[?] => xs.foreach(collect(_, into))
    case _ => ()
  }
}
