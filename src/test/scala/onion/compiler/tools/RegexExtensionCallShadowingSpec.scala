package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `docs/reference/stdlib.md`'s Regex module section documents every
 * `Regex::...` static method but never mentions extension-call syntax at
 * all -- unlike the Strings/Colls/Iterables/Maps/Sets/Stats sections, which
 * all carry an explicit shadowing warning by now. `java.lang.String`
 * already declares instance methods named `matches`, `replace`,
 * `replaceFirst` and `split` with the same arity as their `onion.Regex`
 * counterparts, and an instance method always wins over an extension
 * method of the same name -- the same hazard documented elsewhere.
 *
 * Two distinct traps hide behind that single shadowing mechanism here:
 *
 *   - `matches`/`replaceFirst`/`split` reach a native method that already
 *     uses regex semantics, so for a non-null receiver the *result* agrees
 *     with `onion.Regex`. The divergence only shows up on a `null` platform
 *     receiver: `onion.Regex::...` is null-safe, but dispatching any
 *     instance method (including the native one extension-call syntax
 *     reaches) on a `null` receiver throws `NullPointerException`
 *     regardless of which method it is. `split` has a second-order gap
 *     even for a non-null receiver: the native method returns a raw
 *     `String[]` array, not the `List[String]` every other stdlib
 *     collection-returning method promises (per CLAUDE.md's "the standard
 *     library ... never [returns] arrays" rule).
 *
 *   - `replace` is worse: `java.lang.String.replace(CharSequence,
 *     CharSequence)` performs a **literal** substring replacement, while
 *     `onion.Regex::replace` treats its second argument as a **regex**.
 *     `s.replace(pattern, replacement)` therefore silently reaches the
 *     literal native method, not the regex-based `onion.Regex::replace` --
 *     wrong *results* on an ordinary non-null receiver, not just a crash on
 *     a null one.
 *
 * Locks in both behaviors and checks that both docs (English and Japanese)
 * carry an accurate warning in the Regex module section.
 */
class RegexExtensionCallShadowingSpec extends AbstractShellSpec {

  private def nullPlatformString: String =
    """
      |val m: HashMap[String, String] = new HashMap[String, String]
      |val s: String = m.get("missing") as String
      |""".stripMargin

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "RegexShadowStr.on", Array()))
  }

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "RegexShadowBool.on", Array()))
  }

  private def expectNpe(body: String, fileName: String): Unit = {
    val thrown = intercept[ScriptException] {
      shell.run(
        "class Test {\npublic:\n  static def main(args: String[]): void {\n" + body + "\n  }\n}\n",
        fileName, Array())
    }
    assert(thrown.getCause.isInstanceOf[NullPointerException])
  }

  describe("extension-call syntax for replace() silently switches from regex to literal semantics") {
    it("s.replace(pattern, replacement) treats pattern as a literal substring, not a regex") {
      runStr("return \"a1b22c333\".replace(\"\\\\d+\", \"-\")", Shell.Success("a1b22c333"))
    }

    it("Regex::replace(s, pattern, replacement) treats the same pattern as a regex") {
      runStr("return Regex::replace(\"a1b22c333\", \"\\\\d+\", \"-\")", Shell.Success("a-b-c-"))
    }
  }

  describe("extension-call syntax for matches()/replaceFirst()/split() agrees on non-null input") {
    it("s.matches(pattern) and Regex::matches(s, pattern) agree for a non-null receiver") {
      runBool("return \"abc123\".matches(\"[a-z]+\\\\d+\")", Shell.Success(true))
      runBool("return Regex::matches(\"abc123\", \"[a-z]+\\\\d+\")", Shell.Success(true))
    }

    it("s.replaceFirst(pattern, replacement) and Regex::replaceFirst agree for a non-null receiver") {
      runStr("return \"a1b22c333\".replaceFirst(\"\\\\d+\", \"-\")", Shell.Success("a-b22c333"))
      runStr("return Regex::replaceFirst(\"a1b22c333\", \"\\\\d+\", \"-\")", Shell.Success("a-b22c333"))
    }
  }

  describe("extension-call syntax and onion.Regex diverge on a null platform receiver") {
    it("s.matches(pattern) on a null platform String throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.matches(\"x\")\n", "RegexShadowMatchesNpe.on")
    }

    it("Regex::matches(s, pattern) on the same null platform String is null-safe and returns false") {
      runBool(nullPlatformString + "    return Regex::matches(s, \"x\")", Shell.Success(false))
    }

    it("s.replace(pattern, replacement) on a null platform String throws NullPointerException") {
      expectNpe(nullPlatformString + "    s.replace(\"x\", \"y\")\n", "RegexShadowReplaceNpe.on")
    }

    it("Regex::replace(s, pattern, replacement) on the same null platform String is null-safe and returns \"\"") {
      runStr(nullPlatformString + "    return Regex::replace(s, \"x\", \"y\")", Shell.Success(""))
    }
  }

  describe("docs carry an accurate extension-call shadowing warning in the Regex module section") {
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

    it("docs/reference/stdlib.md's Regex module section warns about extension-call shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Regex")
      assert(doc.contains("shadow"),
        "docs/reference/stdlib.md's Regex section does not mention extension-call shadowing at all")
      assert(doc.contains("`replace`") && doc.contains("`matches`"),
        "docs/reference/stdlib.md's Regex section does not name the shadowed methods")
      assert(doc.contains("s.replace("),
        "docs/reference/stdlib.md's Regex section is missing an s.replace(...) shadowing example")
    }

    it("docs/ja/reference/stdlib.md's Regex section warns about extension-call shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Regex")
      assert(doc.contains("シャドー") || doc.contains("shadow"),
        "docs/ja/reference/stdlib.md's Regex section does not mention extension-call shadowing at all")
      assert(doc.contains("`replace`") && doc.contains("`matches`"),
        "docs/ja/reference/stdlib.md's Regex section does not name the shadowed methods")
      assert(doc.contains("s.replace("),
        "docs/ja/reference/stdlib.md's Regex section is missing an s.replace(...) shadowing example")
    }
  }
}
