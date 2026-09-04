package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Sets` declares `containsAll(Set, Set)` as a builtin extension method
 * on `Set`, but an instance method always wins over an extension method of
 * the same name -- and `java.util.Set` (via `java.util.Collection`) already
 * defines `containsAll(Collection)`. So `a.containsAll(b)` silently reaches
 * the *native JDK method* instead of `onion.Sets`'s, with different `null`
 * semantics: `onion.Sets::containsAll` is null-safe (a `null` subset means
 * "contains all," a `null` container means "contains none"), while native
 * `Set.containsAll` throws `NullPointerException` for a `null` argument.
 * This locks in that shadowing so a future change to extension-method
 * resolution order doesn't silently flip it, and checks that both docs carry
 * the warning (docs/reference/stdlib.md and its Japanese translation, Sets
 * Module section).
 */
class SetsContainsAllExtensionCallShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "SetsContainsAllShadow.on", Array()))
  }

  describe("extension-call syntax for containsAll() on a Set[Int] shadows onion.Sets") {
    it("a.containsAll(null) throws NullPointerException (native Set.containsAll's behavior)") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val n: Set[Int] = null
          |try {
          |  a.containsAll(n)
          |  return false
          |} catch e: NullPointerException {
          |  return true
          |}
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::containsAll(a, null) returns true instead of throwing (null subset means \"contains all\")") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |return onion.Sets::containsAll(a, null)
          |""".stripMargin,
        Shell.Success(true))
    }

    it("onion.Sets::containsAll(null, b) returns false instead of throwing (null container means \"contains none\")") {
      runBool(
        """
          |return onion.Sets::containsAll(null, onion.Sets::of(1))
          |""".stripMargin,
        Shell.Success(false))
    }

    it("non-null arguments agree between extension-call and static-call syntax") {
      runBool(
        """
          |val a = onion.Sets::of(1, 2, 3)
          |val b = onion.Sets::of(1, 2)
          |return a.containsAll(b) && onion.Sets::containsAll(a, b)
          |""".stripMargin,
        Shell.Success(true))
    }
  }

  describe("docs carry the containsAll() shadowing warning in the Sets Module section") {
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

    val enMarkers = Set("a.containsAll(", "java.util.Set", "NullPointerException")
    val jaMarkers = Set("a.containsAll(", "java.util.Set", "NullPointerException")

    it("docs/reference/stdlib.md's Sets Module section warns about containsAll shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Sets Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Sets Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Sets section warns about containsAll shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Sets モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Sets section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
