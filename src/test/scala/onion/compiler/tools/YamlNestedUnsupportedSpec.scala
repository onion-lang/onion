package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml` is documented as flat-only (a flat block mapping, or a top-level block
 * sequence of flat mappings; no nested maps/sequences, no anchors, no flow
 * collections) -- see #2239. Before this spec, both directions silently
 * produced wrong data instead of raising an error for every construct outside
 * that scope.
 */
class YamlNestedUnsupportedSpec extends AbstractShellSpec {
  it("stringify rejects a nested Map value instead of emitting Java toString") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::stringify(["c": ["d": 1L]])
        |     return -1
        |   } catch e: IllegalArgumentException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("stringify rejects a nested List value instead of emitting Java toString") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::stringify(["l": [1L, 2L]])
        |     return -1
        |   } catch e: IllegalArgumentException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("parse rejects an indented child mapping instead of hoisting its keys to the top") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("c:\n  d: 1\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("parse rejects a flow sequence value instead of returning it as a literal String") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("b: [1, 2]\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("parse rejects a flow mapping value instead of returning it as a literal String") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("b: {x: 1}\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("parse rejects an anchor value instead of keeping it as literal text") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("a: &x 1\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("parse rejects an alias value instead of keeping it as literal text") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("b: *x\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return 1
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("still parses an unindented flat mapping normally") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   val m = Yaml::parse("name: Alice\nage: 30\n")
        |   return m.toString()
        | }
      """.stripMargin, "None", Array())
    assert(result match {
      case Shell.Success(s: String) => s.contains("Alice") && s.contains("30")
      case _ => false
    })
  }

  it("still parses a block sequence of flat mappings normally") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val rows = (Yaml::parse("- name: Alice\n  age: 30\n- name: Bob\n  age: 25\n") as List[Object])
        |   return rows.size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }
}
