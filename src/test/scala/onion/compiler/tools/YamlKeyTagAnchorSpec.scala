package onion.compiler.tools

import onion.tools.Shell

/** A tag or anchor on a key is out of scope; it used to come back inside the key text (#2369). */
class YamlKeyTagAnchorSpec extends AbstractShellSpec {
  private def rejects(yaml: String): Shell.Result =
    shell.run(
      s"""
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("$yaml")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())

  it("rejects a tag on a top-level key") {
    assert(Shell.Success(1) == rejects("!!str 5: x\\n"))
  }
  it("rejects a tag on a later key") {
    assert(Shell.Success(1) == rejects("a: 1\\n!!str b: 2\\n"))
  }
  it("rejects a tag on a key inside a sequence item") {
    assert(Shell.Success(1) == rejects("- a: 1\\n  !t b: 2\\n"))
  }
  it("rejects an anchor on a key") {
    assert(Shell.Success(1) == rejects("&x k: 1\\n"))
  }
  it("still reads a quoted key that starts with ! or &") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val m = (Yaml::parse("\"!a\": 1\n\"&b\": 2\n") as Map)
        |   return if m.containsKey("!a") && m.containsKey("&b") { 1 } else { -1 }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }
}
