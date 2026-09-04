package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Stats` and `onion.Colls` are both registered as builtin extension
 * containers (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`),
 * and both declare a `min(List)`/`max(List)` static method with the same
 * erased signature. Extension registration keeps the first container that
 * claims a given receiver+name+erased-signature, and `Colls` is listed
 * ahead of `Stats`, so `xs.min()`/`xs.max()` always reach `onion.Colls`'s
 * versions -- `onion.Stats`'s `min`/`max` are never reachable by
 * extension-call syntax at all. This locks in that shadowing so a future
 * reordering of `BuiltinExtensionContainers` doesn't silently flip it, and
 * checks that both docs carry the warning (docs/reference/stdlib.md and
 * its Japanese translation, Stats Module section).
 */
class StatsExtensionCallShadowingSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StatsShadowInt.on", Array()))
  }

  private def runDouble(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Double {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StatsShadowDouble.on", Array()))
  }

  describe("extension-call syntax for min()/max() on a List[Int] shadows onion.Stats") {
    it("xs.min() returns the exact Int element (Colls's behavior), not a Double") {
      // If this reached onion.Stats::min (which returns Double), a method
      // declared to return Int could not hand its result back without a
      // narrowing conversion, and this would fail to compile.
      runInt("val xs: List[Int] = [10, 20, 30, 40]\n    return xs.min()", Shell.Success(10))
      runInt("val xs: List[Int] = [10, 20, 30, 40]\n    return xs.max()", Shell.Success(40))
    }

    it("xs.min() throws on an empty list instead of returning 0.0") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val xs: List[Int] = []\n    return xs.min()\n  }\n}\n",
          "StatsShadowEmptyMin.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[java.util.NoSuchElementException])
    }

    it("the static Stats:: form keeps onion.Stats's safe empty-list behavior (0.0), never reachable via extension-call syntax") {
      runDouble("val xs: List[Int] = []\n    return Stats::min(xs)", Shell.Success(0.0))
      runDouble("val xs: List[Int] = [10, 20, 30, 40]\n    return Stats::min(xs)", Shell.Success(10.0))
    }

    it("non-colliding Stats methods behave identically via extension-call and static-call syntax") {
      runDouble("val xs: List[Int] = [10, 20, 30, 40]\n    return xs.average()", Shell.Success(25.0))
      runDouble("val xs: List[Int] = [10, 20, 30, 40]\n    return Stats::average(xs)", Shell.Success(25.0))
    }
  }

  describe("docs carry the shadowing warning in the Stats Module section") {
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
      Set("xs.min(", "xs.max(", "onion.Colls", "NoSuchElementException")

    val jaMarkers =
      Set("xs.min(", "xs.max(", "onion.Colls", "NoSuchElementException")

    it("docs/reference/stdlib.md's Stats Module section warns about min()/max() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Stats Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Stats Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Stats section warns about min()/max() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Stats モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Stats section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
