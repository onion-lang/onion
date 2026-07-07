package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for `onion.Format`: locale-independent human-readable formatting of
 * integers (comma grouping), decimals, percentages, byte sizes, durations, and
 * English ordinals.
 */
class FormatSpec extends AbstractShellSpec {

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Format }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "Format.on", Array()))
  }

  describe("Format module") {
    it("groups integers with commas, including negatives") {
      runStr(
        "return Format::integer(1234567L) + \"|\" + Format::integer(-98765L)",
        Shell.Success("1,234,567|-98,765"))
    }

    it("formats grouped decimals, fixed decimals and percentages") {
      runStr(
        "return Format::number(1234.5678, 2) + \"|\" + Format::fixed(3.14159, 2) + \"|\" + Format::percent(0.756, 1)",
        Shell.Success("1,234.57|3.14|75.6%"))
    }

    it("renders human-readable byte sizes (1024-based)") {
      runStr(
        "return Format::bytes(500L) + \"|\" + Format::bytes(1536L) + \"|\" + Format::bytes(5242880L)",
        Shell.Success("500 B|1.5 KB|5.0 MB"))
    }

    it("renders durations from seconds") {
      runStr(
        "return Format::duration(3661L) + \"|\" + Format::duration(90061L) + \"|\" + Format::duration(0L)",
        Shell.Success("1h 1m 1s|1d 1h 1m 1s|0s"))
    }

    it("produces English ordinals with the teens exception") {
      runStr(
        "return Format::ordinal(1L) + \" \" + Format::ordinal(2L) + \" \" + Format::ordinal(3L) + \" \" + Format::ordinal(11L) + \" \" + Format::ordinal(21L)",
        Shell.Success("1st 2nd 3rd 11th 21st"))
    }
  }
}
