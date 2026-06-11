package onion.compiler.tools

import onion.tools.Shell

/**
 * Data-carrying enums: enum Planet(mass: Double) { MERCURY(3.3) } declares
 * record-style parameters that become final fields with accessors; constants
 * pass constructor arguments. values()/valueOf() return real results (they
 * used to be null stubs).
 */
class EnumConstructorArgsSpec extends AbstractShellSpec {

  describe("Enums with constructor arguments") {
    it("exposes constant data through accessors") {
      val result = shell.run(
        """
          |enum Planet(mass: Double) {
          |  MERCURY(3.3),
          |  EARTH(5.97)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + Planet::EARTH.mass() + "," + Planet::MERCURY.mass()
          |  }
          |}
          |""".stripMargin,
        "EnumData.on",
        Array()
      )
      assert(Shell.Success("5.97,3.3") == result)
    }

    it("supports multiple parameters of mixed types") {
      val result = shell.run(
        """
          |enum Http(code: Int, reason: String) {
          |  OK(200, "OK"),
          |  NOT_FOUND(404, "Not Found")
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val nf = Http::NOT_FOUND
          |    return "" + nf.code() + " " + nf.reason()
          |  }
          |}
          |""".stripMargin,
        "EnumMixed.on",
        Array()
      )
      assert(Shell.Success("404 Not Found") == result)
    }

    it("rejects argument count mismatches") {
      val result = shell.run(
        """
          |enum Planet(mass: Double) {
          |  MERCURY(3.3, 9.9)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "no" }
          |}
          |""".stripMargin,
        "EnumArity.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("Enum values() and valueOf()") {
    it("values() returns all constants in declaration order") {
      val result = shell.run(
        """
          |enum Color { RED, GREEN, BLUE }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var r = ""
          |    foreach c: Color in Color::values() {
          |      r = r + c.ordinal() + ":" + c.name() + ";"
          |    }
          |    return r
          |  }
          |}
          |""".stripMargin,
        "EnumValues.on",
        Array()
      )
      assert(Shell.Success("0:RED;1:GREEN;2:BLUE;") == result)
    }

    it("valueOf(String) resolves constants and works with data enums") {
      val result = shell.run(
        """
          |enum Planet(mass: Double) {
          |  MERCURY(3.3),
          |  EARTH(5.97)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + Planet::valueOf("EARTH").mass()
          |  }
          |}
          |""".stripMargin,
        "EnumValueOf.on",
        Array()
      )
      assert(Shell.Success("5.97") == result)
    }
  }
}
