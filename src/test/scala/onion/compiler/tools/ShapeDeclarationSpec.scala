package onion.compiler.tools

import onion.tools.Shell

/**
 * `shape name = re"..."` on a record: a named, first-class `Shape` for that record.
 *
 * `from re"..."` allows exactly one pattern per record and bolts fixed-name statics onto
 * it (`parse`, `parseAll`, `format`), so a type cannot have a v1 and a v2 log format at
 * once, and the failure it reports is a bare `null` that cannot distinguish "this line is
 * not a record" from "this line has a broken field".
 *
 * A shape clause names its shape, so a record may carry as many as it needs, and the
 * result is an ordinary `Shape` value with the whole `Outcome` vocabulary behind it.
 */
class ShapeDeclarationSpec extends AbstractShellSpec {

  describe("a record with a shape clause") {
    it("parses through the named shape") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape text = re"(-?\d+),(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s = Pt::text()
          |    val o = s.parse("3,4")
          |    if o.isBad() { return -1 }
          |    return o.get().x() * 10 + o.get().y()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(34) == r)
    }

    it("prints back, and satisfies parse(print(v)) == Ok(v)") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape text = re"(-?\d+),(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = Pt::text()
          |    val printed = s.print(new Pt(-7, 9))
          |    val back = s.parse(printed)
          |    return printed + "|" + back.get().x() + "," + back.get().y()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("-7,9|-7,9") == r)
    }

    it("carries more than one shape for the same record") {
      // The thing `from re"..."` structurally cannot do.
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape v1 = re"(-?\d+),(-?\d+)"
          |  shape v2 = re"x=(-?\d+) y=(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Pt::v1().parse("1,2")
          |    val b = Pt::v2().parse("x=3 y=4")
          |    return "" + a.get().x() + a.get().y() + b.get().x() + b.get().y()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("1234") == r)
    }
  }

  describe("failure is a defect, not a null") {
    it("distinguishes a non-match from a broken field") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape text = re"(\w+),(\w+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = Pt::text()
          |    val noMatch = s.parse("nope")
          |    val badField = s.parse("abc,4")
          |    return noMatch.defects().size + "/" + badField.defects().size + "/" +
          |           (badField.defects()[0] as Defect).path()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("1/1/x") == r)
    }

    it("reports every broken field at once") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape text = re"(\w+),(\w+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Pt::text().parse("abc,def").defects().size
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }

    it("keeps the good lines and the bad ones, with line numbers") {
      // What parseAll cannot do: it drops the bad lines without trace.
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape text = re"(-?\d+),(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val each = Pt::text().eachLine("1,2\nbroken\n3,4")
          |    val bad = Outcome::defects(each)
          |    return Outcome::values(each).size + "/" + bad.size + "/" +
          |           (bad[0] as Defect).origin().line()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("2/1/2") == r)
    }
  }

  describe("compile-time checks are the same ones `from` gets") {
    it("rejects a capture-group / component-count mismatch") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape bad = re"(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure, got $r")
    }

    it("rejects a malformed regex literal") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape bad = re"([unclosed"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure, got $r")
    }
  }

  describe("a non-invertible pattern") {
    it("still parses, and says it cannot print rather than lacking the method") {
      // `\s+` has no unique rendering. `from re"..."` silently omits `format` here, so a
      // caller meets "method not found"; a shape answers the question directly.
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape loose = re"(-?\d+)\s+(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = Pt::loose()
          |    return s.parse("5   6").get().x() + "/" + s.canPrint()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("5/false") == r)
    }
  }

  describe("a nullable (T?) component (#1969)") {
    it("accepts a nullable String component and gives null for an unmatched optional group") {
      val r = shell.run(
        """
          |record C(kind: String, scope: String?, subject: String) {
          |  shape line = re"(\w+)(?:\(([^)]*)\))?: (.*)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = C::line()
          |    val noScope = s.parse("fix: no scope here").get()
          |    val withScope = s.parse("fix(api): change api").get()
          |    val emptyScope = s.parse("fix(): empty but present").get()
          |    return (noScope.scope() == null) + "|" + withScope.scope() + "|" + (emptyScope.scope() == "")
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("true|api|true") == r)
    }

    it("accepts a nullable Int? component and gives null for an unmatched optional group") {
      val r = shell.run(
        """
          |record Entry(name: String, retries: Int?) {
          |  shape line = re"(\w+)(?: retries=(\d+))?"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = Entry::line()
          |    val withRetries = s.parse("task retries=3").get()
          |    val withoutRetries = s.parse("task").get()
          |    return withRetries.retries() + "|" + (withoutRetries.retries() == null)
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("3|true") == r)
    }

    it("still gives \"\" (not null) for a non-nullable String component's unmatched optional group") {
      val r = shell.run(
        """
          |record C(kind: String, scope: String, subject: String) {
          |  shape line = re"(\w+)(?:\(([^)]*)\))?: (.*)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c = C::line().parse("fix: no scope here").get()
          |    return "[" + c.scope() + "]"
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("[]") == r)
    }

    it("still rejects a nullable component whose inner type is unsupported (E0061)") {
      val r = shell.run(
        """
          |record Inner(x: Int);
          |record R(a: String, b: Inner?) {
          |  shape line = re"(\S+) (\S+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "x" }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure])
    }
  }

  describe("`shape` stays an ordinary identifier") {
    it("does not become a reserved word") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val shape = "circle"
          |    return shape
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("circle") == r)
    }
  }
}
