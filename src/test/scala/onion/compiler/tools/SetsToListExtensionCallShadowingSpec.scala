package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Sets`'s `toList(Set)` has the same erased signature as
 * `onion.Colls`'s `toList(Set)`, so per
 * `ExtensionMethodFallbackSupport.BuiltinExtensionContainers` (`Colls` listed
 * ahead of `Sets`) it is shadowed the same way `union`/`intersection`/
 * `difference`/`map` already are elsewhere in this module -- except `toList`
 * doesn't silently resolve to the earlier container's version at all: `Set`
 * also conforms to `Iterable`, and `onion.Iterables` separately declares a
 * `toList(Iterable)` extension. That's a genuinely different erasure from
 * `Colls`/`Sets`'s `toList(Set)`, so instead of one shadowing the other,
 * `a.toList()` on a `Set` is **ambiguous between `Colls.toList()` and
 * `Iterables.toList()`** and fails to compile with E0006 -- unlike every
 * other method the Sets Module documents as "also a builtin extension
 * method." The three static forms disagree with each other too:
 *   - `Colls::toList` wraps the copy in `Collections.unmodifiableList` and
 *     throws `NullPointerException` on a `null` set (`new ArrayList<>(set)`)
 *   - `Sets::toList` returns a plain mutable `ArrayList` and treats `null`
 *     as empty
 * This locks in that `a.toList()` does not compile for a `Set` receiver, and
 * checks that both docs carry the warning (docs/reference/stdlib.md and its
 * Japanese translation, Sets Module section).
 */
class SetsToListExtensionCallShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "SetsToListShadow.on", Array()))
  }

  describe("extension-call syntax for toList() on a Set[Int] does not compile") {
    it("a.toList() fails to compile -- ambiguous between Colls.toList() and Iterables.toList()") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |return a.toList().size == 3
          |""".stripMargin,
        Shell.Failure(-1))
    }

    it("onion.Colls::toList(a) returns an unmodifiable list") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |try {
          |  onion.Colls::toList(a).add(4)
          |  return false
          |} catch e: UnsupportedOperationException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::toList(a) returns a mutable list, unlike onion.Colls::toList") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val xs = onion.Sets::toList(a)
          |xs.add(4)
          |return xs.size == 4
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Colls::toList(null) throws NullPointerException on a null Set") {
      runBool(
        """
          |val n: Set[Int] = null
          |try {
          |  onion.Colls::toList(n)
          |  return false
          |} catch e: NullPointerException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::toList(null) returns an empty list instead of throwing") {
      runBool(
        """
          |return onion.Sets::toList(null).isEmpty()
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the toList() shadowing warning in the Sets Module section") {
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
      Set("toList", "onion.Colls", "onion.Iterables", "E0006", "NullPointerException")

    val jaMarkers =
      Set("toList", "onion.Colls", "onion.Iterables", "E0006", "NullPointerException")

    it("docs/reference/stdlib.md's Sets Module section warns about toList() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Sets Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Sets Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Sets section warns about toList() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Sets モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Sets モジュール section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
