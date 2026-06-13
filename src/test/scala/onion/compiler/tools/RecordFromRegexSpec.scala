package onion.compiler.tools

import onion.tools.Shell

/**
 * Pattern-attached records: `record Name(c1: T1, ...) from re"..."` derives a typed
 * parser from the record shape. Two static methods are synthesized:
 *
 *   Name::parse(s: String): Name?        - ANCHORED match; converts each capture group
 *                                          to its component type; null on no-match or
 *                                          conversion failure (e.g. NumberFormatException).
 *   Name::parseAll(text: String): List   - splits text into lines, parses each, drops
 *                                          the nulls, returns the matches.
 *
 * Compile-time checks reuse the regex select-pattern machinery: a malformed regex is
 * E0059 and a capture-group / component-count mismatch is E0060. An unsupported
 * component type is E0061.
 */
class RecordFromRegexSpec extends AbstractShellSpec {
  describe("record ... from re\"...\"") {
    it("parses with mixed component types (String + Int)") {
      val result = shell.run(
        """
          |record Access(time: String, method: String, path: String, status: Int)
          |  from re"(\S+) (\w+) (\S+) (\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Access? = Access::parse("127.0.0.1 GET /index 200")
          |    if a != null {
          |      return a.method() + " " + a.path() + " " + (a.status() + 1)
          |    } else {
          |      return "FAIL"
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // status() + 1 == 201 proves status is a real Int, not a String
      assert(Shell.Success("GET /index 201") == result)
    }

    it("returns null when the whole string does not match") {
      val result = shell.run(
        """
          |record Access(time: String, method: String, path: String, status: Int)
          |  from re"(\S+) (\w+) (\S+) (\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Access? = Access::parse("garbage line without enough fields")
          |    if a == null { return "no-match" } else { return "unexpected" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("no-match") == result)
    }

    it("returns null when a capture cannot be converted to the component type") {
      val result = shell.run(
        """
          |record Access(time: String, method: String, path: String, status: Int)
          |  from re"(\S+) (\w+) (\S+) (\w+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    // status capture "OK" is not a number -> NumberFormatException -> null
          |    val a: Access? = Access::parse("t GET /p OK")
          |    if a == null { return "convFail" } else { return "unexpected" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("convFail") == result)
    }

    it("parseAll parses each line and skips the non-matching ones") {
      val result = shell.run(
        """
          |record Access(time: String, method: String, path: String, status: Int)
          |  from re"(\S+) (\w+) (\S+) (\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val text: String = "1.1.1.1 GET /a 200\nbadline\n2.2.2.2 POST /b 404"
          |    val all: List = Access::parseAll(text)
          |    var sum: Int = 0
          |    foreach r: Access in all { sum = sum + r.status() }
          |    return all.size + ":" + sum
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // 2 matches, statuses 200 + 404 = 604
      assert(Shell.Success("2:604") == result)
    }

    it("supports all numeric/boolean component types") {
      val result = shell.run(
        """
          |record Row(s: String, i: Int, l: Long, d: Double, f: Float, b: Boolean, sh: Short, by: Byte)
          |  from re"(\w+) (\d+) (\d+) ([\d.]+) ([\d.]+) (true|false) (\d+) (\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r: Row? = Row::parse("hi 42 9999999999 3.5 2.5 true 7 100")
          |    if r != null {
          |      return r.s() + "|" + r.i() + "|" + r.l() + "|" + r.d() + "|" + r.f() + "|" + r.b() + "|" + r.sh() + "|" + r.by()
          |    } else {
          |      return "FAIL"
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hi|42|9999999999|3.5|2.5|true|7|100") == result)
    }

    it("rejects a capture-group / component-count mismatch at compile time (E0060)") {
      val result = shell.run(
        """
          |record R(a: String, b: String, c: String) from re"(\S+) (\S+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "x" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects a malformed regex literal at compile time (E0059)") {
      val result = shell.run(
        """
          |record R(a: String) from re"([unclosed"
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "x" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects an unsupported component type at compile time (E0061)") {
      val result = shell.run(
        """
          |record Inner(x: Int);
          |record R(a: String, b: Inner) from re"(\S+) (\S+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "x" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still allows `from` as an ordinary identifier") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def from(x: Int): Int { return x + 1 }
          |  static def main(args: String[]): String {
          |    val from: Int = 41
          |    return "" + Test::from(from)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }
  }
}
