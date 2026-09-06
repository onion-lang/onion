package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `docs/reference/stdlib.md`'s Strings Module section documents that several
 * `onion.Strings` methods are shadowed by identically-named `java.lang.String`
 * instance methods when called via extension-call syntax (`trim`,
 * `startsWith`, `endsWith`, `indexOf`, `replace`, ...), but omits two more
 * methods with the exact same hazard: `equalsIgnoreCase` and `lastIndexOf`.
 * `java.lang.String` already defines `equalsIgnoreCase(String)` and
 * `lastIndexOf(String)` instance methods, so `a.equalsIgnoreCase(b)` and
 * `s.lastIndexOf(x)` also silently reach the *native JDK method* instead of
 * `onion.Strings`'s, the same as `indexOf` a few lines above them in the same
 * doc section.
 *
 * For a non-null receiver the native method and `onion.Strings`'s agree, so
 * the shadowing is invisible there. It becomes observable for a receiver
 * that is `null` at runtime but was never checked at compile time: a value
 * read back from unparameterized Java interop (a "platform type", per
 * CLAUDE.md) carries no compile-time nullability tracking, so Onion's
 * null-safety typechecking does not force a null check before
 * `.equalsIgnoreCase(...)`/`.lastIndexOf(...)` on it. `onion.Strings`'s
 * versions are null-safe (`equalsIgnoreCase(null, "x") == false`,
 * `lastIndexOf(null, x) == -1`); the native methods that extension-call
 * syntax actually reaches throw `NullPointerException` instead. Locks in the
 * shadowing and checks that both docs carry an accurate warning
 * (docs/reference/stdlib.md and its Japanese translation, Strings Module
 * section).
 */
class StringsEqualsIgnoreCaseLastIndexOfShadowingSpec extends AbstractShellSpec {

  private def nullPlatformString: String =
    """
      |val m: HashMap[String, String] = new HashMap[String, String]
      |val s: String = m.get("missing") as String
      |""".stripMargin

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsEqIgnLastIdxShadowBool.on", Array()))
  }

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsEqIgnLastIdxShadowInt.on", Array()))
  }

  private def expectNpe(body: String, fileName: String): Unit = {
    val thrown = intercept[ScriptException] {
      shell.run(
        "class Test {\npublic:\n  static def main(args: String[]): void {\n" + body + "\n  }\n}\n",
        fileName, Array())
    }
    assert(thrown.getCause.isInstanceOf[NullPointerException])
  }

  describe("extension-call syntax for equalsIgnoreCase()/lastIndexOf() on a null platform String shadows onion.Strings") {
    it("s.equalsIgnoreCase(x) on a null platform String reaches the native String.equalsIgnoreCase and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.equalsIgnoreCase(\"x\")\n", "StringsEqualsIgnoreCaseShadowNpe.on")
    }

    it("Strings::equalsIgnoreCase(s, x) on the same null platform String is null-safe and returns false instead") {
      runBool(nullPlatformString + "    return Strings::equalsIgnoreCase(s, \"x\")", Shell.Success(false))
    }

    it("s.lastIndexOf(x) on a null platform String reaches the native String.lastIndexOf and throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.lastIndexOf(\"x\")\n", "StringsLastIndexOfShadowNpe.on")
    }

    it("Strings::lastIndexOf(s, x) on the same null platform String is null-safe and returns -1 instead") {
      runInt(nullPlatformString + "    return Strings::lastIndexOf(s, \"x\")", Shell.Success(-1))
    }

    it("for a non-null receiver, extension-call and static-call syntax agree") {
      runBool("return \"Hello\".equalsIgnoreCase(\"HELLO\")", Shell.Success(true))
      runInt("return \"hello\".lastIndexOf(\"l\")", Shell.Success(3))
    }
  }

  describe("docs carry an accurate equalsIgnoreCase()/lastIndexOf() shadowing warning in the Strings Module section") {
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
      Set("equalsIgnoreCase", "lastIndexOf", "NullPointerException")

    val jaMarkers =
      Set("equalsIgnoreCase", "lastIndexOf", "NullPointerException")

    it("docs/reference/stdlib.md's Strings Module section warns about equalsIgnoreCase()/lastIndexOf() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Strings Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
      assert(doc.contains("s.equalsIgnoreCase(") && doc.contains("s.lastIndexOf("),
        "docs/reference/stdlib.md's Strings Module section does not warn about equalsIgnoreCase()/lastIndexOf() extension-call shadowing")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about equalsIgnoreCase()/lastIndexOf() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Strings section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
      assert(doc.contains("s.equalsIgnoreCase(") && doc.contains("s.lastIndexOf("),
        "docs/ja/reference/stdlib.md's Strings section does not warn about equalsIgnoreCase()/lastIndexOf() extension-call shadowing")
    }
  }
}
