package onion.compiler.tools

import onion.tools.Shell

/**
 * `shape name = json` / `yaml` / `csv`: the same named boundary as the regex form, over a
 * structured document instead of a line of text.
 *
 * This is what `derive!(Json, Yaml)` was reaching for. That form bolts fixed-name statics
 * on (`fromJson`/`toJson`/`fromYaml`/`toYaml`), so a record gets one shape per format and
 * no name for it, and it reports failure as `null` — including the case where a missing
 * `String` key produced a *successfully constructed* record with a null non-nullable
 * field, which is worse than an error.
 */
class FormatShapeSpec extends AbstractShellSpec {

  describe("a JSON shape") {
    it("reads a document into a record") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val o = Pt::doc().parse("{\"x\": 3, \"y\": 4}")
          |    if o.isBad() { return -1 }
          |    return o.get().x() * 10 + o.get().y()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(34) == r)
    }

    it("round-trips: parse(print(v)) == Ok(v)") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val s = Pt::doc()
          |    val v = new Pt(-7, 9)
          |    return s.parse(s.print(v)).get() == v
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r)
    }

    it("reports a missing key as a defect instead of building a broken record") {
      // derive!(Json) returns a record with a null non-nullable String field here.
      val r = shell.run(
        """
          |record Person(name: String, age: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Person::doc().parse("{\"age\": 30}")
          |    if o.isOk() { return "silently built: " + o.get().name() }
          |    return (o.defects()[0] as Defect).path() + "/" + (o.defects()[0] as Defect).actual()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("name/absent") == r)
    }

    it("reports every missing key at once") {
      val r = shell.run(
        """
          |record Person(name: String, age: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Person::doc().parse("{}").defects().size
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }

    it("reports malformed JSON with the line it failed on") {
      // Json.JsonParseException carries a character offset that the derive! path throws
      // away; a shape turns it into a position.
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Pt::doc().parse("{\n  \"x\": 1,\n  oops\n}")
          |    if o.isOk() { return "unexpectedly ok" }
          |    return "" + (o.defects()[0] as Defect).origin().line()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("3") == r)
    }

    it("reports a wrongly-typed value as a defect") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Pt::doc().parse("{\"x\": \"hello\", \"y\": 2}")
          |    if o.isOk() { return "unexpectedly ok" }
          |    val d = o.defects()[0] as Defect
          |    return d.path() + "/" + d.expected()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("x/Int") == r)
    }
  }

  describe("a YAML shape") {
    it("round-trips a flat document") {
      val r = shell.run(
        """
          |record Cfg(host: String, port: Int)
          |  shape doc = yaml
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val s = Cfg::doc()
          |    val v = new Cfg("example.com", 8080)
          |    return s.parse(s.print(v)).get() == v
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r)
    }
  }

  describe("several shapes, several formats, one record") {
    it("carries a regex shape and a json shape side by side") {
      // The thing `from re"..." derive!(Json)` can do only with fixed names, one each.
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape text = re"(-?\d+),(-?\d+)"
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Pt::text().parse("1,2").get()
          |    val b = Pt::doc().parse("{\"x\": 3, \"y\": 4}").get()
          |    return "" + a.x() + a.y() + b.x() + b.y()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("1234") == r)
    }
  }

  describe("an unknown format") {
    it("is rejected at compile time, listing what is supported") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape doc = toml
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure, got $r")
    }
  }
}
