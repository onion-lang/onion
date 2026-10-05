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

    it("fromYaml returns null when a String key is missing") {
      // The numeric case above fails via an unboxing NPE that fromMap's try/catch already
      // catches. A missing String key has no such NPE -- Json::getString (shared by the
      // Yaml path too) just returns Java null -- so fromMap must check for it explicitly.
      val result = shell.run(
        """
          |record Pt(name: String, x: Int) derive!(Yaml)
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

    it("rejects an unsupported component type (E0062)") {
      // RecordJsonSpec covers this for derive!(Json); derive!(Yaml) shares the same
      // TypingOutlinePass.isDataDerivableType check (both markers reuse one toMap/fromMap
      // core), but had no direct regression here before this test.
      val result = shell.run(
        """
          |record Inner(z: Int)
          |record Bad(a: String, b: Inner) derive!(Yaml)
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

    it("round-trips a nullable scalar component (String?), present and null (#1969)") {
      val result = shell.run(
        """
          |record Person(name: String, nickname: String?) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v1 = new Person("Ko", "ko-chan")
          |    val v1b = Person::fromYaml(Person::toYaml(v1))
          |    if v1b == null || v1b != v1 { return "present-mismatch" }
          |    val v2 = new Person("Ko", null)
          |    val v2b = Person::fromYaml(Person::toYaml(v2))
          |    if v2b == null || v2b != v2 { return "null-mismatch" }
          |    if v2b.nickname() != null { return "not-null: " + v2b.nickname() }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
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

    it("fromYamlList parses a YAML sequence of flat mappings into a List[R]") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pts = Pt::fromYamlList("- x: 1\n  y: 2\n- x: 3\n  y: 4\n")
          |    if pts.size != 2 { return "size=" + pts.size }
          |    val p0: Pt = pts[0]
          |    val p1: Pt = pts[1]
          |    return "" + (p0.x() + p0.y() + p1.x() + p1.y())
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("10") == result)
    }

    it("fromYamlList skips elements that don't fit, rather than failing the whole sequence") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pts = Pt::fromYamlList("- x: 1\n  y: 2\n- x: 1\n- name: not a point\n")
          |    return "" + pts.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("fromYamlList skips an element missing a String key, not just a numeric one") {
      val result = shell.run(
        """
          |record Item(name: String, qty: Int) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val items = Item::fromYamlList("- name: apple\n  qty: 3\n- qty: 5\n- name: plum\n  qty: 2\n")
          |    return "" + items.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("2") == result)
    }

    it("fromYamlList returns an empty List on malformed YAML or a non-sequence top level") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Pt::fromYamlList("x: 1\ny: 2\n")
          |    val b = Pt::fromYamlList("no colon here")
          |    return "" + a.size + "," + b.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("0,0") == result)
    }
  }
}
