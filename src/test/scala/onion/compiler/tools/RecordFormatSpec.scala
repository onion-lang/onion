package onion.compiler.tools

import onion.tools.Shell

/**
 * Bidirectional pattern-attached records: when a `record ... from re"..."`
 * pattern is invertible (purely literal text + flat top-level capture groups),
 * a `format(v: Name): String` method is synthesized alongside parse/parseAll —
 * the inverse direction. It renders each component into its group's slot, so
 * `parse(format(x)) == x` holds for data that doesn't collide with the
 * literals. Non-invertible patterns (non-literal separators such as `\s+`,
 * `.`, alternation, quantified/nested groups) get no `format`.
 */
class RecordFormatSpec extends AbstractShellSpec {
  describe("derived format (the inverse direction)") {
    it("reconstructs the exact source string") {
      val result = shell.run(
        """
          |record Access(time: String, method: String, path: String, status: Int)
          |  from re"(\S+) (\w+) (\S+) (\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Access::parse("1.2.3.4 GET /index 200")
          |    if a != null { return Access::format(a) }
          |    return "?"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1.2.3.4 GET /index 200") == result)
    }

    it("round-trips parse . format for typed components") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) from re"(\d+),(\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Pt::parse("3,4")
          |    if p == null { return "no-parse" }
          |    val s = Pt::format(p)
          |    val q = Pt::parse(s)
          |    if q == null { return "no-roundtrip" }
          |    return s + ";" + (q.x() + q.y())
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("3,4;7") == result)
    }

    it("formats with literal fragments around groups") {
      val result = shell.run(
        """
          |record Kv(key: String, value: String) from re"(\w+)=(\w+);"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val k = Kv::parse("name=onion;")
          |    if k != null { return Kv::format(k) }
          |    return "?"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("name=onion;") == result)
    }

    it("does NOT derive format for a non-literal separator (\\s+)") {
      val result = shell.run(
        """
          |record Span(a: Int, b: Int) from re"(\d+)\s+(\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = Span::parse("1   2")
          |    if s != null { return Span::format(s) }
          |    return "?"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still parses fine even when format is not derivable") {
      val result = shell.run(
        """
          |record Span(a: Int, b: Int) from re"(\d+)\s+(\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s = Span::parse("10   20")
          |    if s == null { return -1 }
          |    return s.a() + s.b()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(30) == result)
    }
  }
}
