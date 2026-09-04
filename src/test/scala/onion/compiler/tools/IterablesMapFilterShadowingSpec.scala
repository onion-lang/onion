package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Iterables::map(List, Function1)`/`filter(List, Function1)` have the same
 * erased signature as `onion.Colls`'s versions of the same names, and `Colls` is
 * listed ahead of `Iterables` in `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`,
 * so `xs.map(f)` and `xs.filter(predicate)` always reach *`onion.Colls`*'s
 * implementations -- `onion.Iterables`'s versions of these two are never reachable
 * by extension-call syntax at all, even though docs/reference/stdlib.md (and its
 * Japanese translation) show them as plain `Iterables` chaining examples alongside
 * `take`/`drop`/`reverse` (already documented as shadowed) without calling out the
 * same shadowing for `map`/`filter`. The two implementations disagree on edge cases:
 *   - `map`: `Colls::map` returns an unmodifiable list; `Iterables::map` returns a
 *     plain mutable `ArrayList`
 *   - `filter`: `Colls::filter` calls the predicate and auto-unboxes its `Boolean`
 *     result, throwing `NullPointerException` if the predicate returns `null`;
 *     `Iterables::filter` treats a `null` predicate result as "not kept" instead
 * This locks in that shadowing so a future reordering of
 * `BuiltinExtensionContainers` doesn't silently flip it, and checks that both
 * docs carry the warning (docs/reference/stdlib.md and its Japanese translation,
 * Iterables Module section).
 */
class IterablesMapFilterShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "IterablesMapFilterShadow.on", Array()))
  }

  describe("extension-call syntax for map()/filter() on a List[Int] shadows onion.Iterables") {
    it("xs.map(f) is unmodifiable (Colls's behavior), unlike onion.Iterables's mutable copy") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |try {
          |  xs.map((x: Int) -> x * 2).add(8)
          |  return false
          |} catch e: UnsupportedOperationException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("Iterables::map(xs, f) stays mutable, unlike the shadowing extension call") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |Iterables::map(xs, (x: Int) -> x * 2).add(8)
          |return true
          |""".stripMargin,
        Shell.Success(true))
    }

    it("xs.filter(predicate) throws NullPointerException when the predicate returns null (Colls's behavior)") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |try {
          |  xs.filter { x -> null }
          |  return false
          |} catch e: NullPointerException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("Iterables::filter(xs, predicate) treats a null predicate result as not-kept instead") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return Iterables::filter(xs, (x) -> null) == []
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the map()/filter() shadowing warning in the Iterables Module section") {
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

    val enMarkers =
      Set("xs.map {", "xs.filter {", "NullPointerException", "unmodifiable")

    val jaMarkers =
      Set("xs.map {", "xs.filter {", "NullPointerException", "変更不可")

    it("docs/reference/stdlib.md's Iterables Module section warns about map()/filter() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Iterables Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Iterables Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Iterables section warns about map()/filter() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Iterables モジュール section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
