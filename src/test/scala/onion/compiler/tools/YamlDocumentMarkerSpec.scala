package onion.compiler.tools

import onion.tools.Shell

/** `Yaml::parse` skips a leading `---` and a trailing `...`; a second document is rejected. */
class YamlDocumentMarkerSpec extends AbstractShellSpec {
  private def parsed(yaml: String): Shell.Result = shell.run(
    s"""
       |import { onion.Yaml::*; }
       |class Test {
       |public:
       |  static def main(args: String[]): String {
       |    return Yaml::parse("$yaml").toString()
       |  }
       |}
       |""".stripMargin,
    "None",
    Array()
  )

  private def parsedOrNull(yaml: String): Shell.Result = shell.run(
    s"""
       |import { onion.Yaml::*; }
       |class Test {
       |public:
       |  static def main(args: String[]): String {
       |    return "" + Yaml::parseOrNull("$yaml")
       |  }
       |}
       |""".stripMargin,
    "None",
    Array()
  )

  describe("Yaml::parse document markers") {
    it("skips a leading --- in a mapping") {
      assert(Shell.Success("{name=Alice, age=30}") == parsed("---\\nname: Alice\\nage: 30\\n"))
    }
    it("skips a --- after leading comments") {
      assert(Shell.Success("{a=1}") == parsed("# header\\n---\\na: 1\\n"))
    }
    it("skips a trailing ...") {
      assert(Shell.Success("{name=Alice}") == parsed("name: Alice\\n...\\n"))
    }
    it("skips --- and ... around a sequence") {
      assert(Shell.Success("[{n=a}, {n=b}]") == parsed("---\\n- n: a\\n- n: b\\n...\\n"))
    }
    it("a --- alone is an empty mapping") {
      assert(Shell.Success("{}") == parsed("---\\n"))
    }
    it("rejects a second document") {
      assert(Shell.Success("null") == parsedOrNull("a: 1\\n---\\nb: 2\\n"))
    }
    it("rejects data after ...") {
      assert(Shell.Success("null") == parsedOrNull("a: 1\\n...\\nb: 2\\n"))
    }
  }
}
