package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `onion.Sets`'s own javadoc and docs/reference/stdlib.md only ever spell its members as
 * `Sets::name(a, ...)` static calls, but every one of its static methods is also a real
 * extension method on `Set` (`a.union(b)`, `a.isDisjoint(c)`, ... -- `a.union(b)`,
 * `a.intersection(b)`, `a.symmetricDifference(b)`, `a.isSubsetOf(b)` and `a.isDisjoint(b)`
 * are already exercised as compiling/running extension calls by StdlibMethodChainSpec),
 * left undocumented in both languages.
 */
class SetsExtensionCallDocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def setsSection(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toIndexedSeq
    val start = lines.indexWhere(_.contains(heading))
    assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.startsWith("## "))
    (if (end < 0) rest else rest.take(end)).mkString("\n")
  }

  private val extensionCalls = Set(
    "a.union(", "a.intersection(", "a.difference(", "a.symmetricDifference(",
    "a.containsAll(", "a.isSubsetOf(", "a.isSupersetOf(", "a.isDisjoint(",
    "a.map(", "a.filter(", "a.forEach(", "a.count(", "a.any(", "a.all(", "a.find("
  )

  it("docs/reference/stdlib.md's Sets Module section documents Set algebra and functional operations as extension calls") {
    val doc = setsSection(read("docs/reference/stdlib.md"), "## Sets Module")
    val missing = extensionCalls.filterNot(doc.contains)
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md's Sets Module section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Sets section documents Set algebra and functional operations as extension calls") {
    val doc = setsSection(read("docs/ja/reference/stdlib.md"), "## Sets モジュール")
    val missing = extensionCalls.filterNot(doc.contains)
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md's Sets section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
