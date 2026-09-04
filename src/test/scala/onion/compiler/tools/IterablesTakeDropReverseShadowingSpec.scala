package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Iterables::take`/`drop`/`reverse` have the same erased signature as
 * `onion.Colls`'s versions of the same names, and `Colls` is listed ahead of
 * `Iterables` in `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`,
 * so `xs.take(n)`, `xs.drop(n)` and `xs.reverse()` always reach *`onion.Colls`*'s
 * implementations -- `onion.Iterables`'s versions of these three are never
 * reachable by extension-call syntax at all, even though docs/reference/stdlib.md
 * (and its Japanese translation) show them as if they were plain `Iterables`
 * chaining examples. The two implementations disagree on edge cases:
 *   - a negative `n`: `Colls::take`/`drop` clamp to an empty/unchanged result,
 *     `Iterables::take`/`drop` throw (`subList` rejects the negative index)
 *   - `n` at or past the list's size: `Colls::take`/`drop` return the very
 *     same list reference, not a copy; `Iterables::take`/`drop` always copy
 *   - `reverse`: `Colls::reverse` returns an unmodifiable list; `Iterables::reverse`
 *     returns a plain mutable `ArrayList`
 * This locks in that shadowing so a future reordering of
 * `BuiltinExtensionContainers` doesn't silently flip it, and checks that both
 * docs carry the warning (docs/reference/stdlib.md and its Japanese
 * translation, Iterables Module section).
 */
class IterablesTakeDropReverseShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "IterablesShadow.on", Array()))
  }

  describe("extension-call syntax for take()/drop()/reverse() on a List[Int] shadows onion.Iterables") {
    it("xs.take(-1) returns an empty list (Colls's behavior), not a thrown exception") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return xs.take(-1) == []
          |""".stripMargin,
        Shell.Success(true))
    }

    it("Iterables::take(xs, -1) throws instead, unlike the shadowing extension call") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |try {
          |  Iterables::take(xs, -1)
          |  return false
          |} catch e: RuntimeException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("xs.drop(-1) returns the very same list reference (Colls's aliasing behavior)") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return xs.drop(-1) === xs
          |""".stripMargin,
        Shell.Success(true))
    }

    it("Iterables::drop(xs, -1) throws instead, unlike the shadowing extension call") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |try {
          |  Iterables::drop(xs, -1)
          |  return false
          |} catch e: RuntimeException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("xs.take(n) with n past the list's size returns the very same list reference, not a copy") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return xs.take(xs.size() + 1) === xs
          |""".stripMargin,
        Shell.Success(true))
    }

    it("Iterables::take(xs, n) past the size copies instead of aliasing") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |return !(Iterables::take(xs, xs.size() + 1) === xs)
          |""".stripMargin,
        Shell.Success(true))
    }

    it("xs.reverse() is unmodifiable (Colls's behavior), unlike onion.Iterables's mutable copy") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |try {
          |  xs.reverse().add(4)
          |  return false
          |} catch e: UnsupportedOperationException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("Iterables::reverse(xs) stays mutable, unlike the shadowing extension call") {
      runBool(
        """
          |val xs: List[Int] = [1, 2, 3]
          |Iterables::reverse(xs).add(4)
          |return true
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the shadowing warning in the Iterables Module section") {
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
      Set("xs.take(", "xs.drop(", "xs.reverse(", "onion.Colls", "same list reference", "unmodifiable")

    val jaMarkers =
      Set("xs.take(", "xs.drop(", "xs.reverse(", "onion.Colls", "同じリスト参照", "変更不可")

    it("docs/reference/stdlib.md's Iterables Module section warns about take()/drop()/reverse() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Iterables Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Iterables Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Iterables section warns about take()/drop()/reverse() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Iterables モジュール section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
