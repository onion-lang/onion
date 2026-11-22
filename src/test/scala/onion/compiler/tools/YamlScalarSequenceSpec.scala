package onion.compiler.tools

import onion.tools.Shell

/** A top-level sequence of scalars is out of Yaml's scope, and the error says so (#3274). */
class YamlScalarSequenceSpec extends AbstractShellSpec {
  it("a sequence of scalars is reported as out of scope, not as a missing colon") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("- 1\n- 2\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     if e.message().contains("out of scope") && e.message().contains("Json") && !e.message().contains("no colon") {
        |       return e.getLine()
        |     }
        |     return -2
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("a sequence of mappings still parses") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val xs = Yaml::parse("- a: 1\n- a: 2\n") as java.util.List
        |   return xs.size()
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }
}
