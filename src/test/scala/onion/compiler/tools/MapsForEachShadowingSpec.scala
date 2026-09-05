package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Maps.forEach(Map, Function2)` is registered as a builtin extension
 * (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`), so
 * `m.forEach(action)` is reachable as extension-call syntax on any `Map`
 * receiver -- but `java.util.Map` already declares a matching default
 * instance method `forEach(BiConsumer)` (since Java 8), and ordinary
 * instance-method resolution runs before any extension fallback is even
 * consulted. A two-arg Onion lambda SAM-converts to `BiConsumer` the same
 * way it converts to `onion.Maps::forEach`'s `Function2`, so `m.forEach(action)`
 * always reaches the *native* `java.util.Map.forEach`, never
 * `onion.Maps::forEach` -- the same shadowing pattern already documented for
 * `getOrDefault` in this module (`MapsExtensionCallShadowingSpec`).
 *
 * For a non-null receiver that is invisible: both the native method and
 * `onion.Maps::forEach` visit every (key, value) pair in iteration order.
 * It becomes observable for a receiver that is `null` at runtime but was
 * never checked at compile time (a "platform type", per CLAUDE.md, with no
 * compile-time nullability tracking): `onion.Maps::forEach` is null-safe (a
 * `null` map is a no-op), but `m.forEach(action)` on that same null map
 * reaches the native method and throws `NullPointerException` instead --
 * call `Maps::forEach(...)` directly to get the null-safe behavior on a
 * value that might be null.
 *
 * This locks in that shadowing so a future reordering of
 * `BuiltinExtensionContainers` doesn't silently flip it, and checks that
 * both docs carry the warning (docs/reference/stdlib.md and its Japanese
 * translation, Maps Module section).
 */
class MapsForEachShadowingSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "MapsForEachShadow.on", Array()))
  }

  describe("extension-call syntax for forEach() on a Map shadows onion.Maps") {
    it("m.forEach(action) on a non-null Map agrees with the static Maps:: form (native method, both agree)") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2, \"c\": 3]\n" +
        "    var total = 0\n" +
        "    m.forEach((k: String, v: Int) -> { total = total + v })\n" +
        "    return total",
        Shell.Success(6))
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2, \"c\": 3]\n" +
        "    var total = 0\n" +
        "    Maps::forEach(m, (k: String, v: Int) -> { total = total + v })\n" +
        "    return total",
        Shell.Success(6))
    }

    it("m.forEach(action) on a null platform Map reaches the native Map.forEach and throws NullPointerException") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val outer: HashMap[String, Map[String, Int]] = new HashMap[String, Map[String, Int]]\n" +
          "    val m: Map[String, Int] = outer.get(\"missing\") as Map[String, Int]\n" +
          "    m.forEach((k: String, v: Int) -> { })\n" +
          "    return 0\n  }\n}\n",
          "MapsForEachShadowNpe.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }

    it("Maps::forEach(m, action) on the same null platform Map is null-safe and is a no-op") {
      run(
        "val outer: HashMap[String, Map[String, Int]] = new HashMap[String, Map[String, Int]]\n" +
        "    val m: Map[String, Int] = outer.get(\"missing\") as Map[String, Int]\n" +
        "    var total = 0\n" +
        "    Maps::forEach(m, (k: String, v: Int) -> { total = total + v })\n" +
        "    return total",
        Shell.Success(0))
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

    val enMarkers = Set("m.forEach(", "forEach", "native", "platform")
    val jaMarkers = Set("m.forEach(", "forEach", "プラットフォーム")

    it("docs/reference/stdlib.md's Maps Module section warns about forEach() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Maps Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Maps Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Maps section warns about forEach() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Maps モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Maps section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
