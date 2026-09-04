package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `docs/reference/stdlib.md`'s Strings Module section lists `endsWith`
 * alongside `startsWith`/`contains` in the static-call examples, but the
 * "also work as extension-call method chains... with identical behavior to
 * the static form" paragraph's exception list omitted it -- even though
 * `java.lang.String` already defines an `endsWith(String)` instance method,
 * the exact same shadowing hazard already documented for its sibling
 * `startsWith` (and `trim`/`indexOf`) a few paragraphs below in the same
 * section.
 *
 * For a non-null receiver the native method and `onion.Strings`'s agree, so
 * the shadowing is invisible there. It becomes observable for a receiver
 * that is `null` at runtime but was never checked at compile time: a value
 * read back from unparameterized Java interop (a "platform type", per
 * CLAUDE.md) carries no compile-time nullability tracking, so Onion's
 * null-safety typechecking does not force a null check before
 * `.endsWith(...)` on it. `onion.Strings::endsWith` is null-safe
 * (`endsWith(null, x) == false`); the native method that extension-call
 * syntax actually reaches throws `NullPointerException` instead. Locks in
 * the shadowing and checks that both docs carry an accurate warning
 * (docs/reference/stdlib.md and its Japanese translation, Strings Module
 * section).
 */
class StringsEndsWithExtensionCallShadowingSpec extends AbstractShellSpec {

  private def nullPlatformString: String =
    """
      |val m: HashMap[String, String] = new HashMap[String, String]
      |val s: String = m.get("missing") as String
      |""".stripMargin

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsEndsWithShadowBool.on", Array()))
  }

  private def expectNpe(body: String, fileName: String): Unit = {
    val thrown = intercept[ScriptException] {
      shell.run(
        "class Test {\npublic:\n  static def main(args: String[]): void {\n" + body + "\n  }\n}\n",
        fileName, Array())
    }
    assert(thrown.getCause.isInstanceOf[NullPointerException])
  }

  describe("extension-call syntax for endsWith() on a null platform String shadows onion.Strings") {
    it("s.endsWith(x) on a null platform String reaches the native String.endsWith and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.endsWith(\"x\")\n", "StringsEndsWithShadowNpe.on")
    }

    it("Strings::endsWith(s, x) on the same null platform String is null-safe and returns false instead") {
      runBool(nullPlatformString + "    return Strings::endsWith(s, \"x\")", Shell.Success(false))
    }

    it("for a non-null receiver, extension-call and static-call syntax agree") {
      runBool("return \"hello\".endsWith(\"lo\")", Shell.Success(true))
      runBool("return Strings::endsWith(\"hello\", \"lo\")", Shell.Success(true))
    }
  }

  describe("docs carry an accurate endsWith() shadowing warning in the Strings Module section") {
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

    it("docs/reference/stdlib.md's Strings Module section warns about endsWith() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      assert(doc.contains("s.endsWith("),
        "docs/reference/stdlib.md's Strings Module section is missing an endsWith() shadowing-warning marker")
      assert(doc.contains("NullPointerException"),
        "docs/reference/stdlib.md's Strings Module section is missing a NullPointerException marker")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about endsWith() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      assert(doc.contains("s.endsWith("),
        "docs/ja/reference/stdlib.md's Strings section is missing an endsWith() shadowing-warning marker")
      assert(doc.contains("NullPointerException"),
        "docs/ja/reference/stdlib.md's Strings section is missing a NullPointerException marker")
    }
  }
}
