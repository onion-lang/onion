package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Sets::union`/`intersection`/`difference` all take `(Set, Set)`, the
 * exact same erased signature as `onion.Colls`'s versions of the same names,
 * and `Colls` is listed ahead of `Sets` in
 * `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`, so
 * `a.union(b)`, `a.intersection(b)` and `a.difference(b)` always reach
 * *`onion.Colls`*'s implementations -- `onion.Sets`'s versions of these three
 * are never reachable by extension-call syntax at all, even though
 * docs/reference/stdlib.md (and its Japanese translation) show them as if
 * they were plain `Sets` chaining examples. The two implementations disagree
 * on the result: `Colls::union`/`intersection`/`difference` wrap their result
 * unmodifiable; `Sets::union`/`intersection`/`difference` return a plain
 * mutable `LinkedHashSet` (confirmed by running both against `Shell`, below;
 * a null-argument difference also exists source-side -- `Colls` calls
 * `new LinkedHashSet<>(set1)`/`Collection.addAll(null)`, which reject null,
 * while `Sets` treats a null set as empty -- but Onion's null-safety
 * typechecking already rejects passing a nullable `Set?` to these methods'
 * non-null `Set` parameter at compile time, in both the extension-call and
 * `Sets::` spellings alike, so that difference is not independently
 * observable through ordinary typed code and is not asserted here).
 * `Sets::toList` also collides with both `Colls::toList(Set)` and
 * `Iterables::toList(Iterable)`, but that is a compile-time ambiguity
 * (E0005/E0006) rather than silent shadowing, and docs/reference/stdlib.md
 * never shows `a.toList()` as an extension call in the first place, so it is
 * out of scope here.
 * This locks in the union/intersection/difference shadowing so a future
 * reordering of `BuiltinExtensionContainers` doesn't silently flip it, and
 * checks that both docs carry the warning (docs/reference/stdlib.md and its
 * Japanese translation, Sets Module section).
 */
class SetsUnionIntersectionDifferenceShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "SetsShadow.on", Array()))
  }

  describe("extension-call syntax for union()/intersection()/difference() on a Set[Int] shadows onion.Sets") {
    it("a.union(b) is unmodifiable (Colls's behavior), unlike onion.Sets's mutable copy") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val b = onion.Sets::of(3, 4, 5)
          |try {
          |  a.union(b).add(99)
          |  return false
          |} catch e: UnsupportedOperationException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::union(a, b) stays mutable, unlike the shadowing extension call") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val b = onion.Sets::of(3, 4, 5)
          |onion.Sets::union(a, b).add(99)
          |return true
          |""".stripMargin,
        Shell.Success(true))
    }

    it("a.intersection(b) is unmodifiable (Colls's behavior), unlike onion.Sets's mutable copy") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val b = onion.Sets::of(3, 4, 5)
          |try {
          |  a.intersection(b).add(99)
          |  return false
          |} catch e: UnsupportedOperationException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("a.difference(b) is unmodifiable (Colls's behavior), unlike onion.Sets's mutable copy") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val b = onion.Sets::of(3, 4, 5)
          |try {
          |  a.difference(b).add(99)
          |  return false
          |} catch e: UnsupportedOperationException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

  }

  describe("docs carry the shadowing warning in the Sets Module section") {
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
      Set("a.union(", "a.intersection(", "a.difference(", "onion.Colls", "unmodifiable")

    val jaMarkers =
      Set("a.union(", "a.intersection(", "a.difference(", "onion.Colls", "変更不可")

    it("docs/reference/stdlib.md's Sets Module section warns about union()/intersection()/difference() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Sets Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Sets Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Sets section warns about union()/intersection()/difference() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Sets モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Sets モジュール section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
