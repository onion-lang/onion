package onion.compiler.tools

import onion.tools.Shell

/**
 * `derive!(Yaml)` — the same record⇄Map core as `derive!(Json)`, projected through the
 * Yaml stdlib (`toYaml = Yaml::stringify(toMap(v))`, `fromYaml = fromMap(Yaml::parse(s))`).
 * `fromYaml(toYaml(v)) == v` round-trips for scalar components. Coexists with
 * `derive!(Json)` on one record (toMap/fromMap shared, no duplicate synthesis). Yaml is a
 * flat block-mapping subset with Json-compatible scalar type inference.
 */
class RecordYamlSpec extends AbstractShellSpec {
  describe("record ... derive!(Yaml)") {
    it("round-trips scalar components (String/Int/Long/Double/Boolean)") {
      val result = shell.run(
        """
          |record Rec(name: String, age: Int, big: Long, ratio: Double, flag: Boolean) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new Rec("ko", 3, 100L, 3.5, true)
          |    val v2 = Rec::fromYaml(Rec::toYaml(v))
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

    it("reads Float / Short / Byte from YAML and round-trips") {
      val result = shell.run(
        """
          |record N(f: Float, sh: Short, by: Byte) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = N::fromYaml("f: 2.5\nsh: 9\nby: 4")
          |    if v == null { return "null" }
          |    val v2 = N::fromYaml(N::toYaml(v))
          |    if v2 != null && v2 == v { return "ok" } else { return "ng" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("fromYaml returns null on malformed YAML") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Pt::fromYaml("this has no colon")
          |    if p == null { return "null" } else { return "got" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("fromYaml returns null when a numeric key is missing") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Pt::fromYaml("x: 1")
          |    if p == null { return "null" } else { return "got" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("keeps number-looking and bool-looking strings as String (quote round-trip)") {
      val result = shell.run(
        """
          |record W(a: String, b: String) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val w = new W("123", "true")
          |    val w2 = W::fromYaml(W::toYaml(w))
          |    if w2 == null { return "null" }
          |    return w2.a() + "," + w2.b()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("123,true") == result)
    }

    it("coexists with derive!(Json) — all four methods on one record") {
      val result = shell.run(
        """
          |record U(name: String, age: Int) derive!(Json, Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val u = new U("ko", 3)
          |    val viaJson = U::fromJson(U::toJson(u))
          |    val viaYaml = U::fromYaml(U::toYaml(u))
          |    if viaJson != null && viaYaml != null && viaJson == u && viaYaml == u { return "ok" } else { return "ng" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }
}
