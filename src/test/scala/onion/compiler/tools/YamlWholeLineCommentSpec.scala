package onion.compiler.tools

import onion.tools.Shell

/** `Yaml::parse` skips whole-line `# comment` lines, as hand-edited config files have them. */
class YamlWholeLineCommentSpec extends AbstractShellSpec {
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

  describe("Yaml::parse whole-line comments") {
    it("skips a leading comment line in a mapping") {
      assert(Shell.Success("{a=1}") == parsed("# header\\na: 1\\n"))
    }
    it("skips an indented comment between entries") {
      assert(Shell.Success("{a=1, b=2}") == parsed("a: 1\\n  # note\\nb: 2\\n"))
    }
    it("skips comment lines in a sequence of mappings") {
      assert(Shell.Success("[{n=a}, {n=b}]") == parsed("# list\\n- n: a\\n# between\\n- n: b\\n"))
    }
    it("a comment-only document is an empty mapping") {
      assert(Shell.Success("{}") == parsed("# nothing\\n"))
    }
  }
}
