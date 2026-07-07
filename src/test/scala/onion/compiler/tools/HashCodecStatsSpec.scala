package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the new practical modules: `onion.Hash` (md5/sha1/sha256/sha512),
 * `onion.Codec` (base64/hex/url encode+decode), and `onion.Stats` (sum/average/
 * min/max/median/variance/stddev over any `List` of numbers).
 */
class HashCodecStatsSpec extends AbstractShellSpec {

  private def runStr(imports: String, body: String, expect: Shell.Result): Unit = {
    val src =
      imports + "\nclass Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "HashCodecStats.on", Array()))
  }

  private def runInt(imports: String, body: String, expect: Shell.Result): Unit = {
    val src =
      imports + "\nclass Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "HashCodecStatsInt.on", Array()))
  }

  describe("Hash module") {
    it("computes known SHA-256 and MD5 digests") {
      runStr(
        "import { onion.Hash }",
        "return Hash::sha256(\"abc\") + \"|\" + Hash::md5(\"abc\")",
        Shell.Success(
          "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|900150983cd24fb0d6963f7d28e17f72"))
    }

    it("digest lengths match the algorithm") {
      runInt(
        "import { onion.Hash }",
        "return Hash::sha1(\"x\").length() + Hash::sha512(\"x\").length()",
        Shell.Success(168)) // 40 + 128
    }
  }

  describe("Codec module") {
    it("base64 round-trips") {
      runStr(
        "import { onion.Codec }",
        "return Codec::base64Decode(Codec::base64Encode(\"Hello, Onion!\"))",
        Shell.Success("Hello, Onion!"))
    }

    it("hex encodes and round-trips") {
      runStr(
        "import { onion.Codec }",
        "return Codec::hexEncode(\"Hi\") + \"|\" + Codec::hexDecode(\"4869\")",
        Shell.Success("4869|Hi"))
    }

    it("url encoding round-trips a reserved character") {
      runStr(
        "import { onion.Codec }",
        "return Codec::urlDecode(Codec::urlEncode(\"a b&c\"))",
        Shell.Success("a b&c"))
    }
  }

  describe("Stats module") {
    it("sums and averages an Int list") {
      runInt(
        "import { onion.Stats }",
        "val xs: List[Int] = [10, 20, 30, 40]\n" +
        "return Stats::sumInt(xs) + (Stats::average(xs) as Int)",
        Shell.Success(125)) // 100 + 25
    }

    it("min, max and median in double precision") {
      runStr(
        "import { onion.Stats }",
        "val xs: List[Int] = [10, 20, 30, 40]\n" +
        "return Double::toString(Stats::min(xs)) + \"|\" + Double::toString(Stats::max(xs)) + \"|\" + Double::toString(Stats::median(xs))",
        Shell.Success("10.0|40.0|25.0"))
    }

    it("stddev of a known sample") {
      runStr(
        "import { onion.Stats }",
        "return Double::toString(Stats::stddev([2, 4, 4, 4, 5, 5, 7, 9]))",
        Shell.Success("2.0"))
    }

    it("works on a Double list too") {
      runStr(
        "import { onion.Stats }",
        "val ds: List[Double] = [1.5, 2.5, 3.5]\n" +
        "return Double::toString(Stats::average(ds))",
        Shell.Success("2.5"))
    }
  }
}
