package onion.compiler.tools

import onion.tools.Shell

/**
 * Enum members: access sections after the constant list declare instance
 * and static methods on the enum class.
 */
class EnumMethodSpec extends AbstractShellSpec {

  describe("Enum methods") {
    it("declares instance methods using constant data") {
      val result = shell.run(
        """
          |enum Planet(mass: Double) {
          |  MERCURY(3.3),
          |  EARTH(5.97)
          |public:
          |  def heavierThan(other: Planet): Boolean {
          |    return this.mass() > other.mass()
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + Planet::EARTH.heavierThan(Planet::MERCURY) + "," +
          |           Planet::MERCURY.heavierThan(Planet::EARTH)
          |  }
          |}
          |""".stripMargin,
        "EnumInstanceMethod.on",
        Array()
      )
      assert(Shell.Success("true,false") == result)
    }

    it("reports E0042 for non-exhaustive enum select without else") {
      val result = shell.run(
        """
          |enum Color { RED, GREEN, BLUE }
          |class Test {
          |public:
          |  static def label(c: Color): String {
          |    select c {
          |    case Color::RED: return "warm"
          |    }
          |  }
          |  static def main(args: String[]): String { return "no" }
          |}
          |""".stripMargin,
        "EnumNonExhaustive.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts an exhaustive enum select without else") {
      val result = shell.run(
        """
          |enum Color { RED, GREEN, BLUE }
          |class Test {
          |public:
          |  static def label(c: Color): String {
          |    select c {
          |    case Color::RED: return "warm"
          |    case Color::GREEN, Color::BLUE: return "cool"
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return Test::label(Color::RED) + "/" + Test::label(Color::BLUE)
          |  }
          |}
          |""".stripMargin,
        "EnumExhaustive.on",
        Array()
      )
      assert(Shell.Success("warm/cool") == result)
    }

    it("supports expression-bodied and static methods") {
      val result = shell.run(
        """
          |enum Color {
          |  RED, GREEN
          |public:
          |  def tag(): String = "<" + this.name() + ">"
          |  static def count(): Int { return Color::values().length }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Color::RED.tag() + Color::count()
          |  }
          |}
          |""".stripMargin,
        "EnumStaticMethod.on",
        Array()
      )
      assert(Shell.Success("<RED>2") == result)
    }
  }
}
