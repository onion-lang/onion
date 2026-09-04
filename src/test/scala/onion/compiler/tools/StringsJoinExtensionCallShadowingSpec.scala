package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Strings` declares `join(List, String)` and `onion.Colls` declares
 * `join(List, String)` (an alias for `mkString`) with the same erased
 * signature. Both are registered as builtin extension containers
 * (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`), and `Colls`
 * is listed ahead of `Strings`, so `parts.join(sep)` always reaches
 * `onion.Colls`'s version -- `onion.Strings`'s `join` is never reachable by
 * extension-call syntax at all. This is a different shadowing than the one
 * `StringsExtensionCallShadowingSpec` covers (that one is native
 * `java.lang.String` methods winning over `onion.Strings`; this one is
 * `onion.Colls` winning over `onion.Strings` for a `List` receiver -- `join`
 * is not one of the five JDK-shadowed names). The two disagree on a `null`
 * element: `Colls::join`/`mkString` appends `"null"` (via
 * `StringBuilder.append(Object)`), while `Strings::join` throws
 * `NullPointerException` (via `Object::toString` over a `null` element).
 * Locks in the shadowing so a future reordering of
 * `BuiltinExtensionContainers` doesn't silently flip it, and checks that
 * both docs carry the warning (docs/reference/stdlib.md and its Japanese
 * translation, Strings Module section).
 */
class StringsJoinExtensionCallShadowingSpec extends AbstractShellSpec {

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsJoinShadow.on", Array()))
  }

  describe("extension-call syntax for join() on a List[String] shadows onion.Strings") {
    it("parts.join(sep) with a null element appends the literal \"null\" (Colls's behavior), it does not throw") {
      runStr(
        "val parts: List[String] = [\"a\", null, \"c\"]\n    return parts.join(\",\")",
        Shell.Success("a,null,c"))
    }

    it("Strings::join(parts, sep) with a null element throws NullPointerException instead") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): String {\n" +
          "    val parts: List[String] = [\"a\", null, \"c\"]\n    return Strings::join(parts, \",\")\n  }\n}\n",
          "StringsJoinShadowNpe.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }

    it("without a null element, extension-call and static-call syntax agree") {
      runStr(
        "val parts: List[String] = [\"a\", \"b\", \"c\"]\n    return parts.join(\",\")",
        Shell.Success("a,b,c"))
      runStr(
        "val parts: List[String] = [\"a\", \"b\", \"c\"]\n    return Strings::join(parts, \",\")",
        Shell.Success("a,b,c"))
    }
  }

  describe("docs carry the join shadowing warning in the Strings Module section") {
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
      Set("join", "onion.Colls", "NullPointerException")

    val jaMarkers =
      Set("join", "onion.Colls", "NullPointerException")

    it("docs/reference/stdlib.md's Strings Module section warns about join() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Strings Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about join() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Strings section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
