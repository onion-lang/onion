package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for `onion.Text`: word wrapping, indenting, dedenting, and aligned
 * text tables — layout helpers for console output and reports.
 */
class TextSpec extends AbstractShellSpec {

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Text }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "Text.on", Array()))
  }

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Text }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "TextInt.on", Array()))
  }

  describe("Text module") {
    it("word-wraps to the given width at word boundaries") {
      runInt(
        "return Text::wrap(\"the quick brown fox jumps over the lazy dog\", 15).size()",
        Shell.Success(3))
    }

    it("wrap keeps each line within the width") {
      runStr(
        "val lines = Text::wrap(\"the quick brown fox\", 10)\n" +
        "return lines.get(0)",
        Shell.Success("the quick"))
    }

    it("indent prefixes every line") {
      runStr(
        "return Text::indent(\"a\\nb\", \"> \")",
        Shell.Success("> a\n> b"))
    }

    it("dedent strips the common leading whitespace") {
      runStr(
        "return Text::dedent(\"    a\\n    b\\n      c\")",
        Shell.Success("a\nb\n  c"))
    }

    it("table aligns columns to their widest cell") {
      runStr(
        "val rows = [[\"Name\", \"Dept\"], [\"Alice\", \"Eng\"], [\"Bob\", \"Sales\"]]\n" +
        "return Text::table(rows)",
        Shell.Success("Name   Dept\nAlice  Eng\nBob    Sales"))
    }

    it("table counts full-width characters as two columns") {
      runStr(
        "val rows = [[\"name\", \"age\"], [\"\u5c71\u7530\u592a\u90ce\", \"30\"], [\"Bob\", \"5\"]]\n" +
        "return Text::table(rows)",
        Shell.Success("name      age\n\u5c71\u7530\u592a\u90ce  30\nBob       5"))
    }
  }
  describe("Text::wrap on full-width text (#2942)") {
    def wrapped(text: String, width: Int): String = {
      val src =
        "import { onion.Text }\n" +
        "class Test {\npublic:\n  static def main(args: String[]): String {\n" +
        "    return String::join(\"|\", Text::wrap(\"" + text + "\", " + width + "))\n  }\n}\n"
      shell.run(src, "TextWrapWide.on", Array()) match {
        case Shell.Success(v: String) => v
        case other => fail(s"got: $other")
      }
    }

    it("breaks Japanese text with no spaces, counting two columns per character") {
      assert(wrapped("吾輩は猫である。名前はまだ無い。", 10) == "吾輩は猫で|ある。名前|はまだ無|い。")
    }

    it("never starts a line with closing punctuation or ends one with an opening bracket") {
      assert(wrapped("彼は「はい」と言った。", 6) == "彼は|「は|い」と|言っ|た。")
    }

    it("moves an opening bracket that ends a space-separated word to the next line (#3058)") {
      assert(wrapped("あいう「 えお", 4) == "あい|う|「え|お")
    }

    it("measures spaced Japanese words in columns") {
      assert(wrapped("日本語 の 単語 を 空白 で", 10) == "日本語 の|単語 を 空|白 で")
    }

    it("breaks between a full-width character and a Latin word without adding a space") {
      assert(wrapped("Onionは静的型付けの言語です", 12) == "Onionは静的|型付けの言語|です")
    }

    it("keeps Latin-only wrapping unchanged") {
      assert(wrapped("the quick brown fox jumps over the lazy dog", 15) == "the quick brown|fox jumps over|the lazy dog")
    }
  }
}
