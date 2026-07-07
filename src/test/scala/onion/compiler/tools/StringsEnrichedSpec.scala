package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the practical String operations added to `onion.Strings`:
 * capitalize/decapitalize/capitalizeWords, equalsIgnoreCase/containsIgnoreCase,
 * count, removePrefix/removeSuffix, truncate, center, ifBlank, words, chars, and
 * the null-safe parsers toIntOrNull/toLongOrNull/toDoubleOrNull/toIntOr.
 */
class StringsEnrichedSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsEnriched.on", Array()))
  }

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StringsEnrichedInt.on", Array()))
  }

  describe("enriched Strings library") {
    it("capitalize and capitalizeWords adjust case") {
      run(
        "return Strings::capitalize(\"hello\") + \"|\" + Strings::capitalizeWords(\"a b c\")",
        Shell.Success("Hello|A B C"))
    }

    it("removePrefix and removeSuffix strip affixes") {
      run(
        "return Strings::removePrefix(\"unhappy\", \"un\") + \"|\" + Strings::removeSuffix(\"running\", \"ing\")",
        Shell.Success("happy|runn"))
    }

    it("truncate cuts to length with a suffix") {
      run(
        "return Strings::truncate(\"hello world\", 8, \"...\")",
        Shell.Success("hello..."))
    }

    it("center pads on both sides") {
      run(
        "return Strings::center(\"hi\", 6, '*')",
        Shell.Success("**hi**"))
    }

    it("ifBlank falls back on blank input") {
      run(
        "return Strings::ifBlank(\"   \", \"default\") + Strings::ifBlank(\"x\", \"y\")",
        Shell.Success("defaultx"))
    }

    it("count, containsIgnoreCase, equalsIgnoreCase, words, chars") {
      runInt(
        "val c = Strings::count(\"banana\", \"a\")\n" +
        "val ci = if Strings::containsIgnoreCase(\"Hello\", \"ELL\") { 10 } else { 0 }\n" +
        "val ei = if Strings::equalsIgnoreCase(\"ABC\", \"abc\") { 100 } else { 0 }\n" +
        "val w = Strings::words(\"  a  b   c \").length\n" +
        "val ch = Strings::chars(\"abc\").size()\n" +
        "return c + ci + ei + w + ch",
        Shell.Success(119))
    }

    it("null-safe int parsers return values or fallback") {
      runInt(
        "val ok = Strings::toIntOrNull(\"42\") ?: -1\n" +
        "val bad = Strings::toIntOrNull(\"xx\") ?: -1\n" +
        "val orv = Strings::toIntOr(\"nope\", 7)\n" +
        "return ok + bad + orv",
        Shell.Success(48))
    }

    it("toLongOrNull parses longs") {
      run(
        "val lng = Strings::toLongOrNull(\"100\") ?: 0L\n" +
        "return Long::toString(lng)",
        Shell.Success("100"))
    }

    it("toDoubleOrNull parses decimals") {
      run(
        "val d = Strings::toDoubleOrNull(\"3.5\") ?: 0.0\n" +
        "return Double::toString(d)",
        Shell.Success("3.5"))
    }
  }
}
