package onion.compiler.tools

import onion.tools.Shell

/** Round-4 audit fixes: Rand accepting List, enum constant args rejected. */
class StdlibErgonomicsSpec extends AbstractShellSpec {

  describe("Rand with list literals") {
    it("accepts a list literal in choice and shuffle") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b", "c"]
          |    val c = Rand::choice(xs)
          |    val s = Rand::shuffle(xs)
          |    return "" + (c != null) + ":" + s.size()
          |  }
          |}
          |""".stripMargin,
        "RandList.on",
        Array()
      )
      assert(Shell.Success("true:3") == result)
    }
  }

  describe("Enum constant arguments") {
    it("rejects constructor arguments on enum constants with a clear error") {
      val result = shell.run(
        """
          |enum Planet { MERCURY(1.0), EARTH(2.0) }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return Planet::EARTH.name() }
          |}
          |""".stripMargin,
        "EnumArgs.on",
        Array()
      )
      assert(result == Shell.Failure(-1))
    }
  }
}
