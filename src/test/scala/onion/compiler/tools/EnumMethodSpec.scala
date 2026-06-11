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
