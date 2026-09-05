package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Colls.forEach(List, Function1)` is registered as a builtin
 * extension (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`),
 * so `xs.forEach(action)` would ordinarily be reachable as extension-call
 * syntax on any `List` receiver -- but `List` conforms to `java.lang.Iterable`,
 * which already declares a default instance method `forEach(Consumer)`
 * (since Java 8), and ordinary instance-method resolution runs before any
 * extension fallback is even consulted. A one-arg Onion lambda SAM-converts
 * to `Consumer` the same way it converts to `onion.Colls::forEach`'s
 * `Function1`, so `xs.forEach(action)` always reaches the *native*
 * `Iterable.forEach`, never `onion.Colls::forEach` -- the same shadowing
 * pattern already documented for `Maps::forEach` and `Sets::forEach`
 * (`MapsForEachShadowingSpec`, `SetsForEachShadowingSpec`).
 *
 * Unlike those two, though, the shadowing hides no null-safety gap:
 * `onion.Colls::forEach` performs no null check at all (a plain
 * `for (T element : list)` loop), so calling it *directly* on a null
 * platform `List` also throws `NullPointerException`, the same as the
 * native method it's shadowed by. There is no null-safe form to fall back
 * on here.
 *
 * This locks in that shadowing so a future reordering of
 * `BuiltinExtensionContainers` doesn't silently flip it, and checks that
 * both docs carry the warning (docs/reference/stdlib.md and its Japanese
 * translation, Colls Module section).
 */
class CollsForEachShadowingSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "CollsForEachShadow.on", Array()))
  }

  describe("extension-call syntax for forEach() on a List shadows onion.Colls") {
    it("xs.forEach(action) on a non-null List agrees with the static Colls:: form (native method, both agree)") {
      run(
        "val xs: List[Int] = [1, 2, 3]\n" +
        "    var total = 0\n" +
        "    xs.forEach((x: Int) -> { total = total + x })\n" +
        "    return total",
        Shell.Success(6))
      run(
        "val xs: List[Int] = [1, 2, 3]\n" +
        "    var total = 0\n" +
        "    Colls::forEach(xs, (x: Int) -> { total = total + x })\n" +
        "    return total",
        Shell.Success(6))
    }

    it("xs.forEach(action) on a null platform List reaches the native Iterable.forEach and throws NullPointerException") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val outer: HashMap[String, List[Int]] = new HashMap[String, List[Int]]\n" +
          "    val xs: List[Int] = outer.get(\"missing\") as List[Int]\n" +
          "    xs.forEach((x: Int) -> { })\n" +
          "    return 0\n  }\n}\n",
          "CollsForEachShadowNpe.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }

    it("Colls::forEach(xs, action) called directly on that same null platform List is NOT null-safe either -- it also throws NullPointerException") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val outer: HashMap[String, List[Int]] = new HashMap[String, List[Int]]\n" +
          "    val xs: List[Int] = outer.get(\"missing\") as List[Int]\n" +
          "    Colls::forEach(xs, (x: Int) -> { })\n" +
          "    return 0\n  }\n}\n",
          "CollsForEachDirectNpe.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }
  }

  describe("docs carry the shadowing warning in the Colls Module section") {
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

    val enMarkers = Set("xs.forEach(", "forEach", "native", "NullPointerException")
    val jaMarkers = Set("xs.forEach(", "forEach", "ネイティブ", "NullPointerException")

    it("docs/reference/stdlib.md's Colls Module section warns about forEach() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Colls Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Colls Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Colls section warns about forEach() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Colls モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Colls section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
