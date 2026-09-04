package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Colls::first(List)`/`last(List)` have the same erased signature as
 * `onion.Iterables`'s `first(List)`/`last(List)`, and `Colls` is listed ahead
 * of `Iterables` in `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`,
 * so `xs.first()`/`xs.last()` always reach *`onion.Colls`*'s implementations --
 * `onion.Iterables`'s versions of these two are never reachable by
 * extension-call syntax at all, even though docs/reference/stdlib.md (and its
 * Japanese translation) list them among the Iterables Module's plain chaining
 * examples, right next to `take`/`drop`/`reverse`/`reduce` which the doc
 * already flags as shadowed.
 *
 * Unlike those four, `Colls::first`/`last` and `Iterables::first`/`last` are
 * implemented identically (`list.isEmpty() ? null : list.get(0)`, and the
 * equivalent for the last index) -- so the shadowing has no observable
 * runtime effect, and this spec locks in that harmlessness (both the
 * extension call and the explicit `Iterables::` static call must always
 * agree) rather than a behavioral disagreement, and checks that both docs
 * carry a note saying so.
 */
class IterablesFirstLastShadowingSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "IterablesFirstLastShadow.on", Array()))
  }

  describe("extension-call syntax for first()/last() on a List[Int] shadows onion.Iterables harmlessly") {
    it("xs.first() agrees with Iterables::first(xs) on a non-empty list") {
      run(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return xs.first() == Iterables::first(xs)
          |""".stripMargin,
        Shell.Success(true))
    }

    it("xs.last() agrees with Iterables::last(xs) on a non-empty list") {
      run(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return xs.last() == Iterables::last(xs)
          |""".stripMargin,
        Shell.Success(true))
    }

    it("xs.first() and xs.last() are both null on an empty list, same as Iterables::first/last") {
      run(
        """
          |val xs: List[Int] = []
          |return xs.first() == null && xs.last() == null && Iterables::first(xs) == null && Iterables::last(xs) == null
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the first()/last() shadowing note in the Iterables Module section") {
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

    val enMarkers = Set("xs.first(", "xs.last(", "onion.Colls", "identical")
    val jaMarkers = Set("xs.first(", "xs.last(", "onion.Colls", "同一")

    it("docs/reference/stdlib.md's Iterables Module section notes first()/last() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Iterables Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Iterables Module section is missing first()/last() shadowing markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Iterables section notes first()/last() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Iterables モジュール section is missing first()/last() shadowing markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
