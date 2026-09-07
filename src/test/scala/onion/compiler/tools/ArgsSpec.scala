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

    it("does not alias a short flag to a long flag's name") {
      // Args.java's class Javadoc used to read "--verbose / -v style" next to the
      // flag("verbose") example, which reads as though -v sets flag("verbose").
      // It doesn't: a short flag is registered under its own single-character
      // name, independently of any long flag with a similar-sounding name.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Args::parse(args)
          |    return o.flag("verbose") + ":" + o.flag("v")
          |  }
          |}
          |""".stripMargin,
        "ArgsShortNotLongAlias.on",
        Array("-v")
      )
      assert(Shell.Success("false:true") == result)
    }
  }
}
