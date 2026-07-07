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
  }
}
