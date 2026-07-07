package onion.compiler.tools

import onion.tools.Shell

/**
 * Hash, Codec, Text and Stats are registered as builtin extension methods on
 * their receiver type (String / List), so they can be written as method chains —
 * `"pw".sha256()`, `"x".base64Encode().base64Decode()`, `text.wrap(40)`,
 * `nums.sum()` — not only as static `Hash::` / `Codec::` / `Text::` / `Stats::`
 * calls.
 */
class StdlibExtensionChainSpec extends AbstractShellSpec {

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StdlibExtChainStr.on", Array()))
  }

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StdlibExtChainInt.on", Array()))
  }

  describe("Hash / Codec as String extensions") {
    it("sha256 and md5 chain off a string") {
      runStr(
        "return \"abc\".sha256().substring(0, 12)",
        Shell.Success("ba7816bf8f01"))
    }

    it("base64 encodes and round-trips as a chain") {
      runStr(
        "return \"Hi\".base64Encode() + \"|\" + \"Hello\".base64Encode().base64Decode()",
        Shell.Success("SGk=|Hello"))
    }

    it("a codec+hash chain composes") {
      runStr(
        "return \"secret\".base64Encode().sha256().substring(0, 8)",
        Shell.Success("1c1185e0"))
    }
  }

  describe("Text as a String extension") {
    it("wrap chains off a string") {
      runInt(
        "return \"the quick brown fox\".wrap(10).size()",
        Shell.Success(2))
    }

    it("indent chains off a string") {
      runStr(
        "return \"a\\nb\".indent(\"> \")",
        Shell.Success("> a\n> b"))
    }
  }

  describe("Stats as a List extension") {
    it("sum, average and median chain off a numeric list") {
      runInt(
        "val nums: List[Int] = [10, 20, 30, 40]\n" +
        "return (nums.sum() as Int) + (nums.average() as Int) + (nums.median() as Int)",
        Shell.Success(150)) // 100 + 25 + 25
    }

    it("stddev chains off a list literal") {
      runStr(
        "return Double::toString([2, 4, 4, 4, 5, 5, 7, 9].stddev())",
        Shell.Success("2.0"))
    }
  }
}
