package onion.compiler.tools

import onion.tools.Shell

/** Tests for the onion.Args command-line parsing stdlib. */
class ArgsSpec extends AbstractShellSpec {

  describe("Args") {
    it("parses flags, options and positionals") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Args::parse(args)
          |    return o.flag("verbose") + ":" + o.option("output", "default.txt") +
          |      ":" + o.intOption("n", 1) + ":" + o.positional()
          |  }
          |}
          |""".stripMargin,
        "ArgsBasic.on",
        Array("--verbose", "--output=out.txt", "--n", "5", "a.on", "b.on")
      )
      assert(Shell.Success("true:out.txt:5:[a.on, b.on]") == result)
    }

    it("handles short flags and the -- separator") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Args::parse(args)
          |    return o.flag("v") + ":" + o.flag("x") + ":" + o.positional()
          |  }
          |}
          |""".stripMargin,
        "ArgsShort.on",
        Array("-vx", "--", "--not-a-flag")
      )
      assert(Shell.Success("true:true:[--not-a-flag]") == result)
    }
  }
}
