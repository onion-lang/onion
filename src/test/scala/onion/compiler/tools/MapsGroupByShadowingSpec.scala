package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Maps` and `onion.Colls` both declare a `groupBy(List, Function1)`
 * static method with the same erased receiver+name+signature (`Maps.groupBy`
 * groups a `List` by a key function, same as `Colls.groupBy`). Both are
 * registered as builtin extension containers
 * (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`), and `Colls`
 * is listed ahead of `Maps` -- so `xs.groupBy(f)` always reaches
 * `onion.Colls`'s version, `onion.Maps`'s is never reachable by
 * extension-call syntax at all, and the two disagree on `null` handling and
 * mutability: `Colls.groupBy` throws `NullPointerException` on a `null` list
 * and returns an unmodifiable `Map` of unmodifiable inner `List`s, while
 * `Maps.groupBy` returns an empty (mutable) `Map` for a `null` list and
 * mutable `ArrayList` buckets otherwise. This locks in that shadowing so a
 * future reordering of `BuiltinExtensionContainers` doesn't silently flip
 * it, and checks that both docs carry the warning (docs/reference/stdlib.md
 * and its Japanese translation, Maps Module section).
 */
class MapsGroupByShadowingSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "MapsGroupByShadow.on", Array()))
  }

  describe("extension-call syntax for groupBy() on a List shadows onion.Maps") {
    it("xs.groupBy(f) returns an unmodifiable Map of unmodifiable Lists (onion.Colls's behavior), not onion.Maps's mutable one") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val xs: List[Int] = [1, 2, 3]\n    val g = xs.groupBy { x -> x % 2 }\n    g.put(9, [1])\n    return 0\n  }\n}\n",
          "MapsGroupByShadowMutateOuter.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[UnsupportedOperationException])
    }

    it("xs.groupBy(f)'s inner Lists are unmodifiable too (onion.Colls's behavior)") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val xs: List[Int] = [1, 2, 3]\n    val g = xs.groupBy { x -> x % 2 }\n    g[1].add(9)\n    return 0\n  }\n}\n",
          "MapsGroupByShadowMutateInner.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[UnsupportedOperationException])
    }

    it("the static Maps:: form keeps onion.Maps's mutable Map/List result, never reachable via extension-call syntax") {
      run(
        "val xs: List[Int] = [1, 2, 3]\n" +
        "    val g = Maps::groupBy(xs, (x: Int) -> x % 2)\n    g[1].add(9)\n    return g.toString()",
        Shell.Success("{1=[1, 3, 9], 0=[2]}"))
    }
  }

  describe("docs carry the shadowing warning in the Maps Module section") {
    def read(p: String): String =
      java.nio.file.Files.readString(java.nio.file.Path.of(p))

    def section(doc: String, heading: String): String = {
      val lines = doc.linesIterator.toIndexedSeq
      val start = lines.indexWhere(_.contains(heading))
      assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
      val rest = lines.drop(start + 1)
      val end = rest.indexWhere(_.startsWith("## "))
      (if (end < 0) rest else rest.take(end)).mkString(" ")
    }

    val enMarkers = Set("xs.groupBy(", "onion.Colls's `groupBy`", "unmodifiable")
    val jaMarkers = Set("xs.groupBy(", "onion.Colls")

    it("docs/reference/stdlib.md's Maps Module section warns about groupBy() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Maps Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Maps Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Maps section warns about groupBy() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Maps モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Maps section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
