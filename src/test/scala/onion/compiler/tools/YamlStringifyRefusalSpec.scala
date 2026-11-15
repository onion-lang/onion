package onion.compiler.tools

import onion.tools.Shell

/** #2806: `Yaml::stringify` refuses what `Yaml::parse` could not read back (or reads as a String). */
class YamlStringifyRefusalSpec extends AbstractShellSpec {
  private def refusal(expr: String): Shell.Result =
    shell.run(
      s"""
        |import { onion.Yaml::*; }
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    try {
        |      Yaml::stringify($expr)
        |      return "no error"
        |    } catch e: IllegalArgumentException {
        |      return "refused"
        |    }
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    )

  describe("Yaml::stringify refusals") {
    it("refuses a scalar element of a List") {
      assert(Shell.Success("refused") == refusal("""["apple", "banana"]"""))
    }
    it("refuses an empty Map element of a List") {
      assert(Shell.Success("refused") == refusal("""[["x": 1L], [:]]"""))
    }
    it("refuses NaN and an infinity") {
      assert(Shell.Success("refused") == refusal("""["b": Double::NaN]"""))
      assert(Shell.Success("refused") == refusal("""["b": Double::POSITIVE_INFINITY]"""))
    }
    it("still writes a List of non-empty Maps and finite numbers") {
      assert(Shell.Success("no error") == refusal("""[["x": 1.5]]"""))
    }
  }
}
