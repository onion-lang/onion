package onion.compiler.tools

import onion.tools.Shell

/**
 * #2327: two inputs outside `Yaml`'s flat scope still came back as wrong data,
 * with no error: a whole-line flow mapping (`{a: 1, b: 2}` became the one-key
 * map `{{a=1, b: 2}}`) and a tag (`a: !!str 5` became the String `!!str 5`).
 * Both are now a `YamlParseException` naming the construct and its line.
 */
class YamlFlowLineAndTagSpec extends AbstractShellSpec {
  private def messageOf(yaml: String): Shell.Result = shell.run(
    s"""
      | static def main(args: String[]): String {
      |   try {
      |     return "parsed: " + Yaml::parse("$yaml")
      |   } catch e: Yaml.YamlParseException {
      |     return e.getMessage()
      |   }
      | }
    """.stripMargin, "None", Array())

  private def rejects(yaml: String, construct: String, line: Int): Boolean = messageOf(yaml) match {
    case Shell.Success(s: String) => s.contains(construct) && s.contains("line " + line)
    case _ => false
  }

  it("rejects a whole-line flow mapping instead of reading it as a one-key map") {
    assert(rejects("{a: 1, b: 2}\\n", "Flow collections", 1))
  }

  it("rejects a whole-line flow sequence") {
    assert(rejects("[1, 2]\\n", "Flow collections", 1))
  }

  it("rejects a flow mapping after the first line of a mapping") {
    assert(rejects("a: 1\\n{b: 2}\\n", "Flow collections", 2))
  }

  it("rejects a flow mapping as a sequence item") {
    assert(rejects("- {a: 1}\\n", "Flow collections", 1))
  }

  it("rejects a tagged value instead of keeping the tag as text") {
    assert(rejects("a: !!str 5\\n", "Tags", 1))
  }

  it("rejects a local tag") {
    assert(rejects("a: 1\\nb: !point 5\\n", "Tags", 2))
  }

  it("calls a sequence under a key a nested sequence, not a nested mapping") {
    assert(rejects("items:\\n  - a\\n", "Nested sequence", 2))
  }

  it("still reads a quoted value that starts with `!` or `{` as text") {
    assert(messageOf("a: \\\"!!str 5\\\"\\nb: '{x}'\\n") == Shell.Success("parsed: {a=!!str 5, b={x}}"))
  }

  it("still reads a quoted key that starts with `{`") {
    assert(messageOf("\\\"{k}\\\": 1\\n") == Shell.Success("parsed: {{k}=1}"))
  }
}
