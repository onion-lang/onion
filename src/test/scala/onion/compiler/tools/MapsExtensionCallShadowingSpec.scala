package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Maps` and `onion.Colls` are both registered as builtin extension
 * containers (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`),
 * and both declare `keys(Map)`/`values(Map)`/`getOrDefault(Map, K, V)`/
 * `mapValues(Map, Function1)` static methods with the same erased
 * receiver+name+signature. Extension registration keeps the first container
 * that claims a given receiver+name+erased-signature, and `Colls` is listed
 * ahead of `Maps`, so `m.keys()`/`m.values()`/`m.mapValues(...)` always reach
 * `onion.Colls`'s versions -- `onion.Maps`'s are never reachable by
 * extension-call syntax at all. `Colls`'s results are unmodifiable;
 * `Maps::keys`/`values`/`mapValues` called directly return a plain mutable
 * `ArrayList`/`LinkedHashMap`. This locks in that shadowing so a future
 * reordering of `BuiltinExtensionContainers` doesn't silently flip it, and
 * checks that both docs carry the warning (docs/reference/stdlib.md and
 * its Japanese translation, Maps Module section).
 */
class MapsExtensionCallShadowingSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "MapsShadow.on", Array()))
  }

  describe("extension-call syntax for keys()/values()/mapValues() on a Map shadows onion.Maps") {
    it("m.keys() returns an unmodifiable List (onion.Colls's behavior), not onion.Maps's mutable one") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val m: Map[String, Int] = [\"a\": 1]\n    m.keys().add(\"z\")\n    return 0\n  }\n}\n",
          "MapsShadowKeysMutate.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[UnsupportedOperationException])
    }

    it("m.values() returns an unmodifiable List (onion.Colls's behavior), not onion.Maps's mutable one") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val m: Map[String, Int] = [\"a\": 1]\n    m.values().add(9)\n    return 0\n  }\n}\n",
          "MapsShadowValuesMutate.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[UnsupportedOperationException])
    }

    it("m.mapValues(f) returns an unmodifiable Map (onion.Colls's behavior), not onion.Maps's mutable one") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val m: Map[String, Int] = [\"a\": 1]\n    m.mapValues((v: Int) -> v * 2).put(\"b\", 2)\n    return 0\n  }\n}\n",
          "MapsShadowMapValuesMutate.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[UnsupportedOperationException])
    }

    it("the static Maps:: form keeps onion.Maps's mutable List/Map results, never reachable via extension-call syntax") {
      run(
        "val m: Map[String, Int] = [\"a\": 1]\n" +
        "    val ks = Maps::keys(m)\n    ks.add(\"z\")\n    return ks.toString()",
        Shell.Success("[a, z]"))
      run(
        "val m: Map[String, Int] = [\"a\": 1]\n" +
        "    val mv = Maps::mapValues(m, (v: Int) -> v * 2)\n    mv.put(\"b\", 4)\n    return mv.toString()",
        Shell.Success("{a=2, b=4}"))
    }

    it("non-colliding Maps methods behave identically via extension-call and static-call syntax") {
      run(
        "val m: Map[String, Int] = [\"a\": 1]\n    return m.getOrDefault(\"x\", 0).toString()",
        Shell.Success("0"))
      run(
        "val m: Map[String, Int] = [\"a\": 1]\n    return Maps::getOrDefault(m, \"x\", 0).toString()",
        Shell.Success("0"))
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
      (if (end < 0) rest else rest.take(end)).mkString("\n")
    }

    val enMarkers =
      Set("m.keys(", "m.values(", "m.mapValues(", "onion.Colls", "unmodifiable")

    val jaMarkers =
      Set("m.keys(", "m.values(", "m.mapValues(", "onion.Colls")

    it("docs/reference/stdlib.md's Maps Module section warns about keys()/values()/mapValues() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Maps Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Maps Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Maps section warns about keys()/values()/mapValues() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Maps モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Maps section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
