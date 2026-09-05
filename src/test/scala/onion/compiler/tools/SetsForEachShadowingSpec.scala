package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Sets.forEach(Set, Function1)` is registered as a builtin extension
 * (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`), so
 * `a.forEach(action)` would ordinarily be reachable as extension-call syntax
 * on any `Set` receiver -- but `Set` conforms to `java.lang.Iterable`, which
 * already declares a default instance method `forEach(Consumer)` (since Java
 * 8), and ordinary instance-method resolution runs before any extension
 * fallback is even consulted. A one-arg Onion lambda SAM-converts to
 * `Consumer` the same way it converts to `onion.Sets::forEach`'s
 * `Function1`, so `a.forEach(action)` always reaches the *native*
 * `Iterable.forEach`, never `onion.Sets::forEach` -- the same shadowing
 * pattern already documented for `Maps::forEach` in this module
 * (`MapsForEachShadowingSpec`).
 *
 * For a non-null receiver that is invisible: both the native method and
 * `onion.Sets::forEach` visit every element in iteration order. It becomes
 * observable for a receiver that is `null` at runtime but was never checked
 * at compile time (a "platform type", per CLAUDE.md, with no compile-time
 * nullability tracking): `onion.Sets::forEach` is null-safe (a `null` set is
 * a no-op), but `a.forEach(action)` on that same null set reaches the native
 * method and throws `NullPointerException` instead -- call
 * `Sets::forEach(...)` directly to get the null-safe behavior on a value
 * that might be null.
 *
 * This locks in that shadowing so a future reordering of
 * `BuiltinExtensionContainers` doesn't silently flip it, and checks that
 * both docs carry the warning (docs/reference/stdlib.md and its Japanese
 * translation, Sets Module section).
 */
class SetsForEachShadowingSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "SetsForEachShadow.on", Array()))
  }

  describe("extension-call syntax for forEach() on a Set shadows onion.Sets") {
    it("a.forEach(action) on a non-null Set agrees with the static Sets:: form (native method, both agree)") {
      run(
        "val a: Set[Int] = Sets::fromList([1, 2, 3])\n" +
        "    var total = 0\n" +
        "    a.forEach((x: Int) -> { total = total + x })\n" +
        "    return total",
        Shell.Success(6))
      run(
        "val a: Set[Int] = Sets::fromList([1, 2, 3])\n" +
        "    var total = 0\n" +
        "    Sets::forEach(a, (x: Int) -> { total = total + x })\n" +
        "    return total",
        Shell.Success(6))
    }

    it("a.forEach(action) on a null platform Set reaches the native Iterable.forEach and throws NullPointerException") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
          "    val outer: HashMap[String, Set[Int]] = new HashMap[String, Set[Int]]\n" +
          "    val a: Set[Int] = outer.get(\"missing\") as Set[Int]\n" +
          "    a.forEach((x: Int) -> { })\n" +
          "    return 0\n  }\n}\n",
          "SetsForEachShadowNpe.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }

    it("Sets::forEach(a, action) on the same null platform Set is null-safe and is a no-op") {
      run(
        "val outer: HashMap[String, Set[Int]] = new HashMap[String, Set[Int]]\n" +
        "    val a: Set[Int] = outer.get(\"missing\") as Set[Int]\n" +
        "    var total = 0\n" +
        "    Sets::forEach(a, (x: Int) -> { total = total + x })\n" +
        "    return total",
        Shell.Success(0))
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

    val enMarkers = Set("a.forEach(", "forEach", "native", "platform")
    val jaMarkers = Set("a.forEach(", "forEach", "プラットフォーム")

    it("docs/reference/stdlib.md's Sets Module section warns about forEach() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Sets Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Sets Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Sets section warns about forEach() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Sets モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Sets section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
