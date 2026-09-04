package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Iterables::map(Set, Function1)` has the same erased signature as
 * `onion.Sets`'s `map(Set, Function1)`, and `Iterables` is listed ahead of
 * `Sets` in `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`, so
 * `a.map(f)` on a `Set` always reaches *`onion.Iterables`*'s implementation
 * -- `onion.Sets`'s `map` is never reachable by extension-call syntax at
 * all, even though docs/reference/stdlib.md (and its Japanese translation)
 * list `map` among the Sets Module's "Functional operations" as if
 * `a.map(...)` reached `onion.Sets::map`. The two implementations disagree:
 *   - order: `Sets::map` collects into a `LinkedHashSet` (insertion-order
 *     preserved, matching the Sets Module's documented promise that "result
 *     sets preserve insertion order"); `Iterables::map` collects into a
 *     plain `HashSet`, so the result's iteration order is unspecified
 *   - null: `Sets::map` treats a `null` set as empty; `Iterables::map`
 *     iterates its argument directly and throws `NullPointerException`
 * This locks in that shadowing (via the resulting `Set` implementation's
 * class name, rather than a specific hash-bucket order, so the assertion
 * doesn't depend on `HashSet`'s internal layout for particular elements) so
 * a future reordering of `BuiltinExtensionContainers` doesn't silently flip
 * it, and checks that both docs carry the warning (docs/reference/stdlib.md
 * and its Japanese translation, Sets Module section).
 */
class SetsMapExtensionCallShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "SetsMapShadow.on", Array()))
  }

  describe("extension-call syntax for map() on a Set[Int] shadows onion.Sets") {
    it("a.map(f) collects into a plain HashSet (Iterables's behavior), not a LinkedHashSet") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |return a.map((x: Int) -> x * 2).getClass().getName() == "java.util.HashSet"
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::map(a, f) collects into a LinkedHashSet, unlike the shadowing extension call") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |return onion.Sets::map(a, (x: Int) -> x * 2).getClass().getName() == "java.util.LinkedHashSet"
          |""".stripMargin,
        Shell.Success(true))
    }

    it("a.map(f) throws NullPointerException on a null Set (Iterables's behavior)") {
      runBool(
        """
          |val n: Set[Int] = null
          |try {
          |  n.map((x: Int) -> x * 2)
          |  return false
          |} catch e: NullPointerException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::map(null, f) returns an empty set instead of throwing") {
      runBool(
        """
          |return onion.Sets::map(null, (x: Int) -> x * 2) == onion.Sets::of()
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the map() shadowing warning in the Sets Module section") {
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
      Set("a.map(", "onion.Iterables", "HashSet", "NullPointerException")

    val jaMarkers =
      Set("a.map(", "onion.Iterables", "HashSet", "NullPointerException")

    it("docs/reference/stdlib.md's Sets Module section warns about map() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Sets Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Sets Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Sets section warns about map() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Sets モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Sets モジュール section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
