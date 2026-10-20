package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml::stringify` must quote a String value that starts with a double quote,
 * or `Yaml::parse` reads it back as a quoted scalar (quotes lost) or fails on
 * an unterminated quote (#2187).
 */
class YamlLeadingDoubleQuoteSpec extends AbstractShellSpec {
  private def roundTrip(value: String): Shell.Result =
    shell.run(
      s"""
        |import { onion.Yaml::*; java.util.Map; }
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val m = ["k": "$value"]
        |    val back = (Yaml::parseOrNull(Yaml::stringify(m)) as Map)
        |    if back == null { return "unreadable" }
        |    return "" + back.get("k")
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    )

  describe("Yaml::stringify leading double quote") {
    it("keeps a value wrapped in double quotes") {
      assert(Shell.Success("\"dq\"") == roundTrip("\\\"dq\\\""))
    }
    it("keeps a value with an unterminated leading double quote") {
      assert(Shell.Success("\"open") == roundTrip("\\\"open"))
    }
  }
}
