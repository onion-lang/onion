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

    it("round-trips a Float component read from JSON") {
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

    it("round-trips a Float component built from a literal `f`/`F` suffix") {
      val result = shell.run(
        """
          |record F(ratio: Float) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v1 = new F(2.5f)
          |    val v2 = new F(3.5F)
          |    val r1 = F::fromJson(F::toJson(v1))
          |    val r2 = F::fromJson(F::toJson(v2))
          |    if r1 != null && r1 == v1 && r2 != null && r2 == v2 { return "ok" } else { return "ng" }
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

    it("fromJson returns null when a String key is missing") {
      // The numeric case above fails via an unboxing NPE that fromMap's try/catch already
      // catches. A missing String key has no such NPE -- Json::getString just returns Java
      // null -- so fromMap must check for it explicitly, or a record declared with a
      // non-nullable `String` component silently ends up holding a null field.
      val result = shell.run(
        """
          |record Pt(name: String, x: Int) derive!(Json)
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

    it("round-trips a present nullable scalar component (String?) (#1969)") {
      val result = shell.run(
        """
          |record Person(name: String, nickname: String?) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new Person("Ko", "ko-chan")
          |    val v2 = Person::fromJson(Person::toJson(v))
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

    it("round-trips a nullable scalar component that is null, as a real JSON null (#1969)") {
      val result = shell.run(
        """
          |record Person(name: String, nickname: String?) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new Person("Ko", null)
          |    val j = Person::toJson(v)
          |    if !j.contains("\"nickname\":null") { return "not-null-literal: " + j }
          |    val v2 = Person::fromJson(j)
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

    it("reads an absent key as null for a nullable scalar component (#1969)") {
      val result = shell.run(
        """
          |record Person(name: String, nickname: String?) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = Person::fromJson("{\"name\": \"Ko\"}")
          |    if v == null { return "null" }
          |    if v.nickname() == null { return "ok" } else { return "not-null: " + v.nickname() }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("a nullable Int? component present with the wrong JSON type still fails the whole record (#1969)") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int?) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = Person::fromJson("{\"name\": \"Ko\", \"age\": \"not-a-number\"}")
          |    if v == null { return "null" } else { return "built" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
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

    it("fromJsonList parses a JSON array into a List[R]") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pts = Pt::fromJsonList("[{\"x\": 1, \"y\": 2}, {\"x\": 3, \"y\": 4}]")
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

    it("fromJsonList skips elements that don't fit, rather than failing the whole array") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pts = Pt::fromJsonList("[{\"x\": 1, \"y\": 2}, {\"x\": 1}, \"not an object\"]")
          |    return "" + pts.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("fromJsonList skips an element missing a String key, not just a numeric one") {
      val result = shell.run(
        """
          |record Item(name: String, qty: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val items = Item::fromJsonList("[{\"name\": \"apple\", \"qty\": 3}, {\"qty\": 5}, {\"name\": \"plum\", \"qty\": 2}]")
          |    return "" + items.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("2") == result)
    }

    it("fromJsonList returns an empty List on malformed JSON or a non-array top level") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Pt::fromJsonList("not json at all")
          |    val b = Pt::fromJsonList("{\"x\": 1, \"y\": 2}")
          |    return "" + a.size + "," + b.size
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("0,0") == result)
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
