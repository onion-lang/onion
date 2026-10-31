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

    it("hexDecode rejects odd length and non-hex characters") {
      runStr(
        "import { onion.Codec }",
        """var odd: String = "none"
          |var bad: String = "none"
          |try { Codec::hexDecode("abc") } catch e: IllegalArgumentException { odd = "rejected" }
          |try { Codec::hexDecode("zz") } catch e: IllegalArgumentException { bad = "rejected" }
          |return odd + "|" + bad + "|" + Codec::hexDecode("").length()""".stripMargin,
        Shell.Success("rejected|rejected|0"))
    }

    it("base64Decode skips line breaks and spaces, as in a file or wrapped CLI output") {
      runStr(
        "import { onion.Codec }",
        "return Codec::base64Decode(\"aGVsbG8=\\n\") + \"|\" + Codec::base64Decode(\"aGVs\\r\\nbG8g\\nd29y bGQ=\")",
        Shell.Success("hello|hello world"))
    }

    it("base64Decode names the bad character, its index, and a bad length") {
      runStr(
        "import { onion.Codec }",
        """var bad: String = "none"
          |var len: String = "none"
          |try { Codec::base64Decode("not base64!!") } catch e: IllegalArgumentException { bad = e.getMessage() }
          |try { Codec::base64Decode("a") } catch e: IllegalArgumentException { len = e.getMessage() }
          |return bad + "|" + len""".stripMargin,
        Shell.Success("invalid Base64 character '!' at index 10|invalid Base64 length or padding: 1 character(s) after removing whitespace"))
    }

    it("base64Decode skips a leading byte order mark, as in a file saved as UTF-8 with BOM (#2454)") {
      runStr(
        "import { onion.Codec }",
        "return Codec::base64Decode(\"\\uFEFFaGVsbG8=\\r\\n\")",
        Shell.Success("hello"))
    }

    it("base64Decode shows an invisible or non-ASCII bad character as U+XXXX (#2454)") {
      runStr(
        "import { onion.Codec }",
        "var nbsp: String = \"none\"\n" +
        "var bom: String = \"none\"\n" +
        "var ctl: String = \"none\"\n" +
        "try { Codec::base64Decode(\"aGVs\\u00A0bG8=\") } catch e: IllegalArgumentException { nbsp = e.getMessage() }\n" +
        "try { Codec::base64Decode(\"aGVs\\uFEFFbG8=\") } catch e: IllegalArgumentException { bom = e.getMessage() }\n" +
        "try { Codec::base64Decode(\"aGVs\\u000CbG8=\") } catch e: IllegalArgumentException { ctl = e.getMessage() }\n" +
        "return nbsp + \"|\" + bom + \"|\" + ctl",
        Shell.Success(
          "invalid Base64 character U+00A0 at index 4" +
          "|invalid Base64 character U+FEFF at index 4" +
          "|invalid Base64 character U+000C at index 4"))
    }

    it("base64Decode names an emoji by its code point, not half a surrogate pair (#2470)") {
      runStr(
        "import { onion.Codec }",
        "var m: String = \"none\"\n" +
        "try { Codec::base64Decode(\"aGVs\\uD83D\\uDE00bG8=\") } catch e: IllegalArgumentException { m = e.getMessage() }\n" +
        "return m",
        Shell.Success("invalid Base64 character U+1F600 at index 4"))
    }

    it("urlDecode names the bad % escape and its index") {
      runStr(
        "import { onion.Codec }",
        """var a: String = "none"
          |var b: String = "none"
          |try { Codec::urlDecode("100%zz") } catch e: IllegalArgumentException { a = e.getMessage() }
          |try { Codec::urlDecode("50%") } catch e: IllegalArgumentException { b = e.getMessage() }
          |return a + "|" + b""".stripMargin,
        Shell.Success(
          "invalid % escape at index 3 in \"100%zz\": a % must be followed by two hex digits|" +
          "invalid % escape at index 2 in \"50%\": a % must be followed by two hex digits"))
    }

    it("urlDecode quotes a short window around the bad escape in a long input, not all of it") {
      runStr(
        "import { onion.Codec }",
        """val sb = new StringBuilder()
          |foreach i in 1..1000 { sb.append("a") }
          |sb.append("%zz")
          |foreach i in 1..1000 { sb.append("b") }
          |var m: String = "none"
          |try { Codec::urlDecode(sb.toString()) } catch e: IllegalArgumentException { m = e.getMessage() }
          |return m""".stripMargin,
        Shell.Success(
          "invalid % escape at index 1000 in \"...aaaaaaaaaaaaaaaaaaaa%zzbbbbbbbbbbbbbbbbbbbb...\": " +
          "a % must be followed by two hex digits"))
    }

    it("urlDecode's quoted window never cuts an emoji in half (#2470)") {
      runStr(
        "import { onion.Codec }",
        """val e = "😀"
          |var head: String = "none"
          |var tail: String = "none"
          |try { Codec::urlDecode("a".repeat(30) + e + "a".repeat(19) + "%zz" + "b".repeat(50)) }
          |catch x: IllegalArgumentException { head = x.getMessage() }
          |try { Codec::urlDecode("a".repeat(100) + "%zz" + "b".repeat(19) + e + "b".repeat(50)) }
          |catch x: IllegalArgumentException { tail = x.getMessage() }
          |return head + "|" + tail""".stripMargin,
        Shell.Success(
          "invalid % escape at index 51 in \"...😀" + "a" * 19 + "%zz" + "b" * 20 + "...\": " +
          "a % must be followed by two hex digits|" +
          "invalid % escape at index 100 in \"..." + "a" * 20 + "%zz" + "b" * 19 + "😀...\": " +
          "a % must be followed by two hex digits"))
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

    it("sums a List[Int] in double precision via the generic static form") {
      runStr(
        "import { onion.Stats }",
        "val xs: List[Int] = [10, 20, 30, 40]\n" +
        "return Double::toString(Stats::sum(xs))",
        Shell.Success("100.0"))
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

    it("variance of a known sample") {
      runStr(
        "import { onion.Stats }",
        "return Double::toString(Stats::variance([2, 4, 4, 4, 5, 5, 7, 9]))",
        Shell.Success("4.0"))
    }

    it("sums a Long list exactly") {
      runStr(
        "import { onion.Stats }",
        "val xs: List[Long] = [10L, 20L, 30L]\n" +
        "return Long::toString(Stats::sumLong(xs))",
        Shell.Success("60"))
    }

    it("works on a Double list too") {
      runStr(
        "import { onion.Stats }",
        "val ds: List[Double] = [1.5, 2.5, 3.5]\n" +
        "return Double::toString(Stats::average(ds))",
        Shell.Success("2.5"))
    }

    it("returns 0.0 from every aggregate over an empty list, per the documented contract") {
      // Stats.java's class doc promises 0.0 (never NaN/exception) for sum, average,
      // min, max, median, variance and stddev when the list is empty.
      runStr(
        "import { onion.Stats }",
        "val xs: List[Double] = []\n" +
        "return Double::toString(Stats::sum(xs)) + \"|\" +" +
        " Double::toString(Stats::average(xs)) + \"|\" +" +
        " Double::toString(Stats::min(xs)) + \"|\" +" +
        " Double::toString(Stats::max(xs)) + \"|\" +" +
        " Double::toString(Stats::median(xs)) + \"|\" +" +
        " Double::toString(Stats::variance(xs)) + \"|\" +" +
        " Double::toString(Stats::stddev(xs))",
        Shell.Success("0.0|0.0|0.0|0.0|0.0|0.0|0.0"))
    }

    it("average and variance skip null elements the way sum, median and min/max do") {
      runStr(
        "import { onion.Stats }",
        "val xs: List[JInteger] = [1, null, 3]\n" +
        "return Double::toString(Stats::average(xs)) + \"|\" + Double::toString(Stats::variance(xs))",
        Shell.Success("2.0|1.0"))
    }

    it("sumInt / sumLong throw on overflow instead of wrapping") {
      runStr(
        "import { onion.Stats }",
        "var a: String = \"none\"\n" +
        "var b: String = \"none\"\n" +
        "try { Stats::sumInt([2147483647, 1]); a = \"wrapped\" } catch e: ArithmeticException { a = \"overflow\" }\n" +
        "try { Stats::sumLong([9223372036854775807L, 1L]); b = \"wrapped\" } catch e: ArithmeticException { b = \"overflow\" }\n" +
        "return a + \"|\" + b",
        Shell.Success("overflow|overflow"))
    }
  }
}
