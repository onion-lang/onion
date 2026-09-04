package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Strings` declares `contains(String, String)` and `isEmpty(String)`,
 * but `java.lang.String` already defines instance methods with these same
 * names (`contains(CharSequence)`, `isEmpty()`), and an instance method
 * always wins over an extension method of the same name -- the same hazard
 * `StringsExtensionCallShadowingSpec` documents for `split`/`substring`/
 * `lines`/`chars`/`repeat`, but that spec's list stops at those five and
 * does not cover these two.
 *
 * For a non-null receiver the native method and `onion.Strings`'s agree
 * (both ultimately call the same JDK logic), so the shadowing is invisible
 * there. It becomes observable for a receiver that is `null` at runtime but
 * was never checked at compile time: a value read back from unparameterized
 * Java interop (a "platform type", per CLAUDE.md) carries no compile-time
 * nullability tracking, so Onion's null-safety typechecking does not force
 * a null check before `.contains(...)`/`.isEmpty()` on it. `onion.Strings`'s
 * versions are null-safe (`isEmpty(null) == true`, `contains(null, x) ==
 * false`); the native methods that extension-call syntax actually reaches
 * throw `NullPointerException` instead. Locks in the shadowing (a future
 * reordering of `BuiltinExtensionContainers` or the resolution rule
 * wouldn't be caught by unit tests exercising only non-null receivers), and
 * checks that both docs carry the warning (docs/reference/stdlib.md and its
 * Japanese translation, Strings Module section).
 */
class StringsContainsIsEmptyExtensionCallShadowingSpec extends AbstractShellSpec {

  private def nullPlatformString: String =
    """
      |val m: HashMap[String, String] = new HashMap[String, String]
      |val s: String = m.get("missing") as String
      |""".stripMargin

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsContainsIsEmptyShadow.on", Array()))
  }

  describe("extension-call syntax for contains()/isEmpty() on a null platform String shadows onion.Strings") {
    it("s.isEmpty() on a null platform String reaches the native String.isEmpty and throws NullPointerException") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" +
          nullPlatformString + "    return s.isEmpty()\n  }\n}\n",
          "StringsContainsIsEmptyShadowNpe.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }

    it("Strings::isEmpty(s) on the same null platform String is null-safe and returns true instead") {
      runBool(nullPlatformString + "    return Strings::isEmpty(s)", Shell.Success(true))
    }

    it("s.contains(x) on a null platform String reaches the native String.contains and throws NullPointerException") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" +
          nullPlatformString + "    return s.contains(\"x\")\n  }\n}\n",
          "StringsContainsIsEmptyShadowNpe2.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[NullPointerException])
    }

    it("Strings::contains(s, x) on the same null platform String is null-safe and returns false instead") {
      runBool(nullPlatformString + "    return Strings::contains(s, \"x\")", Shell.Success(false))
    }

    it("for a non-null receiver, extension-call and static-call syntax agree") {
      runBool("return \"hello\".isEmpty()", Shell.Success(false))
      runBool("return \"hello\".contains(\"ell\")", Shell.Success(true))
    }
  }

  describe("docs carry the contains()/isEmpty() shadowing warning in the Strings Module section") {
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
      Set("s.contains(", "s.isEmpty()", "platform", "NullPointerException")

    val jaMarkers =
      Set("s.contains(", "s.isEmpty()", "プラットフォーム", "NullPointerException")

    it("docs/reference/stdlib.md's Strings Module section warns about contains()/isEmpty() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Strings Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about contains()/isEmpty() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Strings section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
