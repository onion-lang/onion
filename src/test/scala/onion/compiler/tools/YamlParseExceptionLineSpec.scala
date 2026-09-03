package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml.YamlParseException` carries the line number where parsing gave up via
 * `getLine()`, in addition to the usual `message()` -- documented in
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md but never exercised by a
 * `shell.run` test case.
 */
class YamlParseExceptionLineSpec extends AbstractShellSpec {
  it("getLine() reports the line number where parsing failed") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("no colon here")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("getLine() reports the failing line, not the first, on a later bad line") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("name: Alice\nage: 30\nno colon here")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("message() and getLine() are both usable on the caught exception") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   try {
        |     Yaml::parse("no colon here")
        |     return "no exception"
        |   } catch e: Yaml.YamlParseException {
        |     return e.message() + "|" + e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(result match {
      case Shell.Success(s: String) => s.contains("|1")
      case _ => false
    })
  }
}
