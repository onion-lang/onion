package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `docs/reference/stdlib.md`'s Strings Module section opens by claiming
 * "most `Strings` methods ... also work as extension-call method chains
 * ... with identical behavior to the static form," and never lists
 * `replace` among the documented exceptions (`split`/`substring`/`lines`/
 * `chars`/`repeat`, `join`, `contains`/`isEmpty`, `trim`/`startsWith`/
 * `endsWith`/`indexOf`, `isBlank`). That is false: `java.lang.String`
 * already defines an instance method `replace(CharSequence, CharSequence)`,
 * and an instance method always wins over an extension method of the same
 * name -- the same hazard already documented for `trim`/`startsWith`/
 * `endsWith`/`indexOf` a few paragraphs below in the same section.
 *
 * For a non-null receiver the native method and `onion.Strings`'s agree, so
 * the shadowing is invisible there. It becomes observable for a receiver
 * that is `null` at runtime but was never checked at compile time: a value
 * read back from unparameterized Java interop (a "platform type", per
 * CLAUDE.md) carries no compile-time nullability tracking, so Onion's
 * null-safety typechecking does not force a null check before
 * `.replace(...)` on it. `onion.Strings::replace(null, ...)` is null-safe
 * (returns `""`); the native method that extension-call syntax actually
 * reaches throws `NullPointerException` instead (dispatching any instance
 * method on a null receiver throws, regardless of which method). Locks in
 * the shadowing and checks that both docs carry an accurate warning
 * (docs/reference/stdlib.md and its Japanese translation, Strings Module
 * section).
 */
class StringsReplaceExtensionCallShadowingSpec extends AbstractShellSpec {

  private def nullPlatformString: String =
    """
      |val m: HashMap[String, String] = new HashMap[String, String]
      |val s: String = m.get("missing") as String
      |""".stripMargin

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsReplaceShadowStr.on", Array()))
  }

  private def expectNpe(body: String, fileName: String): Unit = {
    val thrown = intercept[ScriptException] {
      shell.run(
        "class Test {\npublic:\n  static def main(args: String[]): void {\n" + body + "\n  }\n}\n",
        fileName, Array())
    }
    assert(thrown.getCause.isInstanceOf[NullPointerException])
  }

  describe("extension-call syntax for replace() on a null platform String shadows onion.Strings") {
    it("s.replace(a, b) on a null platform String reaches the native String.replace and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.replace(\"a\", \"b\")\n", "StringsReplaceShadowNpe.on")
    }

    it("Strings::replace(s, a, b) on the same null platform String is null-safe and returns \"\" instead") {
      runStr(nullPlatformString + "    return Strings::replace(s, \"a\", \"b\")", Shell.Success(""))
    }

    it("for a non-null receiver, extension-call and static-call syntax agree") {
      runStr("return \"banana\".replace(\"a\", \"o\")", Shell.Success("bonono"))
      runStr("return Strings::replace(\"banana\", \"a\", \"o\")", Shell.Success("bonono"))
    }
  }

  describe("docs carry an accurate replace() shadowing warning in the Strings Module section") {
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

    it("docs/reference/stdlib.md's Strings Module section warns about replace() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      assert(doc.contains("`replace`"),
        "docs/reference/stdlib.md's Strings Module section does not mention replace() shadowing at all")
      assert(doc.contains("s.replace("),
        "docs/reference/stdlib.md's Strings Module section is missing an s.replace(...) shadowing example")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about replace() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      assert(doc.contains("`replace`"),
        "docs/ja/reference/stdlib.md's Strings section does not mention replace() shadowing at all")
      assert(doc.contains("s.replace("),
        "docs/ja/reference/stdlib.md's Strings section is missing an s.replace(...) shadowing example")
    }
  }
}
