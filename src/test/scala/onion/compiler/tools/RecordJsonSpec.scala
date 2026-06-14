package onion.compiler.tools

import onion.tools.Shell

/**
 * Bidirectional `derive!(Json)`: a record's shape derives both directions of JSON
 * serialization. `derive!` is a macro-style code derivation (the `!` marks expansion at
 * the use site), not a type class. Two static methods are synthesized:
 *
 *   Name::fromJson(s: String): Name?   - parses JSON, fills components by name; null on
 *                                        parse failure or a missing/wrong-typed numeric key.
 *   Name::toJson(v: Name): String      - renders the record as a JSON object string.
 *
 * They round-trip: fromJson(toJson(v)) == v for scalar components. Unsupported component
 * types are E0062; unknown markers are E0063. Coexists with a `from re"..."` clause.
 */
class RecordJsonSpec extends AbstractShellSpec {
  describe("record ... derive!(Json)") {
    it("round-trips scalar components (String/Int/Long/Double/Boolean)") {
      val result = shell.run(
        """
          |record Rec(name: String, age: Int, big: Long, ratio: Double, flag: Boolean) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new Rec("ko", 3, 100L, 3.5, true)
          |    val v2 = Rec::fromJson(Rec::toJson(v))
          |    if v2 == null { return "null" }
          |    if v2 == v { return "ok" } else { return "mismatch" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    // NB: a Float component is read from JSON rather than built with a `2.5f` literal —
    // the `f`/`F` literal suffix is currently unparsed by the lexer (unrelated to derive!).
    it("round-trips a Float component") {
      val result = shell.run(
        """
          |record F(ratio: Float) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = F::fromJson("{\"ratio\": 2.5}")
          |    if v == null { return "null" }
          |    val v2 = F::fromJson(F::toJson(v))
          |    if v2 != null && v2 == v { return "ok" } else { return "ng" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("reads Short and Byte components from JSON") {
      val result = shell.run(
        """
          |record S(a: Short, b: Byte) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = S::fromJson("{\"a\": 1, \"b\": 2}")
          |    if v == null { return "null" }
          |    return "" + (v.a() + v.b())
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("3") == result)
    }

    it("toJson renders fields the parser reads back (real values, not strings)") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Pt::fromJson(Pt::toJson(new Pt(3, 4)))
          |    if p == null { return "null" }
          |    return "" + (p.x() + p.y())
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("7") == result)
    }

    it("fromJson returns null on malformed JSON") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Pt::fromJson("not json at all")
          |    if p == null { return "null" } else { return "got" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("fromJson returns null when a numeric key is missing") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Pt::fromJson("{\"x\": 1}")
          |    if p == null { return "null" } else { return "got" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("rejects an unsupported component type (E0062)") {
      val result = shell.run(
        """
          |record Inner(z: Int)
          |record Bad(a: String, b: Inner) derive!(Json)
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

    it("rejects an unknown derive! marker (E0063)") {
      val result = shell.run(
        """
          |record U(a: String) derive!(Bogus)
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

    it("coexists with a from re\"...\" clause (parse + fromJson + toJson)") {
      val result = shell.run(
        """
          |record Access(host: String, status: Int)
          |  from re"(\S+) (\d+)" derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Access::parse("1.2.3.4 200")
          |    if a == null { return "noparse" }
          |    val a2 = Access::fromJson(Access::toJson(a))
          |    if a2 != null && a2 == a { return "ok" } else { return "ng" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("keeps `derive` usable as an ordinary identifier") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val derive = 41
          |    return "" + (derive + 1)
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
