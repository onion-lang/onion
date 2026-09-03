package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell

/**
 * `onion.Strings` is registered as a builtin extension container
 * (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`), but an instance
 * method always wins over an extension method of the same name -- and
 * `java.lang.String` already defines `split`, `substring`, `lines`, `chars` and
 * `repeat`. So calling those five by extension-call syntax silently reaches the
 * *native JDK method* instead of `onion.Strings`'s, with different semantics
 * (array instead of List, a thrown exception instead of a safe fallback). This
 * locks in that shadowing so a future change to extension-method resolution
 * order doesn't silently flip it, and checks that both docs carry the warning
 * (docs/reference/stdlib.md and its Japanese translation, Strings Module
 * section).
 */
class StringsExtensionCallShadowingSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsShadowBool.on", Array()))
  }

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsShadowStr.on", Array()))
  }

  describe("extension-call syntax on String shadows onion.Strings for five methods") {
    it("split() reaches the native String.split, not Strings::split -- it returns an array, not a List") {
      // A List has no .length property; only an array does. If the extension
      // call reached onion.Strings::split (which returns a List), this would
      // fail to compile instead of running.
      runBool("return \"a,b,c\".split(\",\").length == 3", Shell.Success(true))
    }

    it("substring() reaches the native String.substring -- it throws on an out-of-range start instead of returning \"\"") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): String {\n" +
          "    return \"abc\".substring(10)\n  }\n}\n",
          "StringsShadowSubstring.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[StringIndexOutOfBoundsException])
    }

    it("repeat() reaches the native String.repeat -- it throws on a negative count instead of returning \"\"") {
      val thrown = intercept[ScriptException] {
        shell.run(
          "class Test {\npublic:\n  static def main(args: String[]): String {\n" +
          "    return \"abc\".repeat(-1)\n  }\n}\n",
          "StringsShadowRepeat.on", Array())
      }
      assert(thrown.getCause.isInstanceOf[IllegalArgumentException])
    }

    it("the static Strings:: form keeps onion.Strings's safe behavior for the same inputs") {
      runStr("return Strings::substring(\"abc\", 10)", Shell.Success(""))
      runStr("return Strings::repeat(\"abc\", -1)", Shell.Success(""))
    }

    it("non-colliding Strings methods behave identically via extension-call and static-call syntax") {
      runStr("return \"Hello\".upper()", Shell.Success("HELLO"))
      runBool("return \"Hello\".startsWith(\"He\")", Shell.Success(true))
    }
  }

  describe("docs carry the shadowing warning in the Strings Module section") {
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
      Set("s.split(", "s.substring(", "s.lines()", "s.chars()", "s.repeat(", "java.lang.String")

    val jaMarkers =
      Set("s.split(", "s.substring(", "s.lines()", "s.chars()", "s.repeat(", "java.lang.String")

    it("docs/reference/stdlib.md's Strings Module section warns about the five shadowed methods") {
      val doc = section(read("docs/reference/stdlib.md"), "## Strings Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Strings Module section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Strings section warns about the five shadowed methods") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Strings section is missing shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
