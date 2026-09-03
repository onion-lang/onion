package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Iterables`'s own javadoc and docs/reference/stdlib.md only ever spell its members as
 * `Iterables::name(list, ...)` static calls, but every one of its static methods except
 * `listOf`/`newList` (which build a `List` rather than operate on one) is also a real
 * extension method, callable as a chain on its first argument (`xs.map(f)`, `xs.take(2)`,
 * `(1..5).toList()`, ...), left undocumented in both languages.
 */
class IterablesExtensionCallDocSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "IterablesExtCall.on", Array()))
  }

  describe("onion.Iterables methods work as extension calls, not just Iterables:: static calls") {
    it("map/filter/exists/forAll/take/drop/reverse/sort/first/last/foldl/reduce/toList/mapMap chain on their receiver") {
      runBool(
        """
          |val xs: List[Int] = [3, 1, 2]
          |if (!xs.exists((x: Int) -> x > 2)) { return false }
          |if (!xs.forAll((x: Int) -> x > 0)) { return false }
          |if (xs.first() != 3) { return false }
          |if (xs.last() != 2) { return false }
          |if (xs.take(2) != [3, 1]) { return false }
          |if (xs.drop(1) != [1, 2]) { return false }
          |if (xs.foldl(0, (acc: Int, x: Int) -> acc + x) != 6) { return false }
          |if (xs.reduce(0, (acc: Int, x: Int) -> acc + x) != 6) { return false }
          |if (xs.sort() != [1, 2, 3]) { return false }
          |if (xs.reverse() != [2, 1, 3]) { return false }
          |if ((1..3).toList() != [1, 2, 3]) { return false }
          |val m: Map[String, Int] = ["a": 1, "b": 2]
          |val doubled: Map[String, Int] = m.mapMap((e) -> Colls::entry(e.getKey(), e.getValue() * 2))
          |if (doubled.get("a") != 2) { return false }
          |if (doubled.get("b") != 4) { return false }
          |return true
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the Iterables extension-call spellings") {
    def read(p: String): String =
      java.nio.file.Files.readString(java.nio.file.Path.of(p))

    def section(doc: String, heading: String): String = {
      val lines = doc.linesIterator.toIndexedSeq
      val start = lines.indexWhere(_.contains(heading))
      assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
      val rest = lines.drop(start + 1)
      val end = rest.indexWhere(_.startsWith("## "))
      (if (end < 0) rest else rest.take(end)).mkString("\n")
    }

    val extensionCalls = Set(
      "xs.map {", "m.mapMap(", ".toList()", "xs.filter {", "xs.foldl(", "xs.reduce(",
      "xs.exists {", "xs.forAll {", "xs.first(", "xs.last(", "xs.reverse(",
      "xs.take(", "xs.drop(", "xs.sort("
    )

    it("docs/reference/stdlib.md's Iterables Module section documents its methods as extension calls") {
      val doc = section(read("docs/reference/stdlib.md"), "## Iterables Module")
      val missing = extensionCalls.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Iterables Module section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Iterables section documents its methods as extension calls") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
      val missing = extensionCalls.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Iterables モジュール section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
