package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `docs/reference/stdlib.md`'s Strings Module section claims `trim`,
 * `startsWith` and `indexOf` are among the `onion.Strings` methods that
 * "also work as extension-call method chains... with identical behavior to
 * the static form." That is false: `java.lang.String` already defines
 * instance methods with these same names (`trim()`, `startsWith(String)`,
 * `indexOf(String)`), and an instance method always wins over an extension
 * method of the same name -- the same hazard already documented for
 * `contains`/`isEmpty` a few paragraphs below in the same section.
 *
 * For a non-null receiver the native method and `onion.Strings`'s agree, so
 * the shadowing is invisible there. It becomes observable for a receiver
 * that is `null` at runtime but was never checked at compile time: a value
 * read back from unparameterized Java interop (a "platform type", per
 * CLAUDE.md) carries no compile-time nullability tracking, so Onion's
 * null-safety typechecking does not force a null check before
 * `.trim()`/`.startsWith(...)`/`.indexOf(...)` on it. `onion.Strings`'s
 * versions are null-safe (`trim(null) == ""`, `startsWith(null, x) ==
 * false`, `indexOf(null, x) == -1`); the native methods that extension-call
 * syntax actually reaches throw `NullPointerException` instead (dispatching
 * any instance method on a null receiver throws, regardless of which
 * method). Locks in the shadowing and checks that both docs carry an
 * accurate warning instead of the false "identical behavior" claim
 * (docs/reference/stdlib.md and its Japanese translation, Strings Module
 * section).
 */
class StringsTrimStartsWithIndexOfExtensionCallShadowingSpec extends AbstractShellSpec {

  private def nullPlatformString: String =
    """
      |val m: HashMap[String, String] = new HashMap[String, String]
      |val s: String = m.get("missing") as String
      |""".stripMargin

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsTrimShadowStr.on", Array()))
  }

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsTrimShadowBool.on", Array()))
  }

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsTrimShadowInt.on", Array()))
  }

  private def expectNpe(body: String, fileName: String): Unit = {
    val thrown = intercept[ScriptException] {
      shell.run(
        "class Test {\npublic:\n  static def main(args: String[]): void {\n" + body + "\n  }\n}\n",
        fileName, Array())
    }
    assert(thrown.getCause.isInstanceOf[NullPointerException])
  }

  describe("extension-call syntax for trim()/startsWith()/indexOf() on a null platform String shadows onion.Strings") {
    it("s.trim() on a null platform String reaches the native String.trim and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.trim()\n", "StringsTrimShadowNpe.on")
    }

    it("Strings::trim(s) on the same null platform String is null-safe and returns \"\" instead") {
      runStr(nullPlatformString + "    return Strings::trim(s)", Shell.Success(""))
    }

    it("s.startsWith(x) on a null platform String reaches the native String.startsWith and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.startsWith(\"x\")\n", "StringsStartsWithShadowNpe.on")
    }

    it("Strings::startsWith(s, x) on the same null platform String is null-safe and returns false instead") {
      runBool(nullPlatformString + "    return Strings::startsWith(s, \"x\")", Shell.Success(false))
    }

    it("s.indexOf(x) on a null platform String reaches the native String.indexOf and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.indexOf(\"x\")\n", "StringsIndexOfShadowNpe.on")
    }

    it("Strings::indexOf(s, x) on the same null platform String is null-safe and returns -1 instead") {
      runInt(nullPlatformString + "    return Strings::indexOf(s, \"x\")", Shell.Success(-1))
    }

    it("for a non-null receiver, extension-call and static-call syntax agree") {
      runStr("return \"  hi  \".trim()", Shell.Success("hi"))
      runBool("return \"hello\".startsWith(\"he\")", Shell.Success(true))
      runInt("return \"hello\".indexOf(\"l\")", Shell.Success(2))
    }
  }

  describe("docs carry an accurate trim()/startsWith()/indexOf() shadowing warning in the Strings Module section, not the false \"identical behavior\" claim") {
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
      Set("s.trim()", "s.startsWith(", "s.indexOf(", "NullPointerException")

    val jaMarkers =
      Set("s.trim()", "s.startsWith(", "s.indexOf(", "NullPointerException")

    it("docs/reference/stdlib.md's Strings Module section warns about trim()/startsWith()/indexOf() shadowing, and no longer claims identical behavior for them") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Strings Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
      assert(!doc.contains("`trim`, `startsWith`, `indexOf`"),
        "docs/reference/stdlib.md still lists trim/startsWith/indexOf as having \"identical behavior\" via extension-call syntax")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about trim()/startsWith()/indexOf() shadowing, and no longer claims identical behavior for them") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Strings section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
      assert(!doc.contains("`trim`、`startsWith`、`indexOf`"),
        "docs/ja/reference/stdlib.md still lists trim/startsWith/indexOf as having identical behavior via extension-call syntax")
    }
  }
}
