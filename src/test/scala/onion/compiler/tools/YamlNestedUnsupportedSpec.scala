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

  // Panel block on #2243, repro 1: the writer must quote what the reader rejects.
  it("round-trips a derive!(Yaml) record whose strings start with a YAML indicator") {
    val result = shell.run(
      """
        |record Note(title: String, body: String) derive!(Yaml)
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val n = new Note("[draft] plan", "*important*")
        |    return "" + (Note::fromYaml(Note::toYaml(n)) == n)
        |  }
        |}
      """.stripMargin, "None", Array())
    assert(Shell.Success("true") == result)
  }

  it("round-trips every string that starts with a YAML indicator through stringify and parse") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   val m = ["a": "[x]", "b": "{y}", "c": "&z", "d": "*w", "e": "|", "f": ">-", "g": "!tag", "h": "%p", "i": "@q", "j": "`r", "k": "[ok"]
        |   return "" + (Yaml::parse(Yaml::stringify(m)) == m)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("true") == result)
  }

  // Panel block on #2243, repro 2: nesting inside a sequence element was still flattened.
  it("parse rejects a nested mapping inside a sequence element") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("- name: a\n  meta:\n    k: v\n- name: b\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("still parses a sequence whose items are uniformly indented") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   return Yaml::parse("  - name: a\n    age: 1\n  - name: b\n").toString()
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("[{name=a, age=1}, {name=b}]") == result)
  }

  // Panel block on #2243: a uniformly indented flat document parsed before the PR.
  it("still parses a uniformly indented flat mapping") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   return Yaml::parse("  a: 1\n  b: 2\n").toString()
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("{a=1, b=2}") == result)
  }

  it("parse rejects a nested mapping after a dedent in an indented document") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("  a: 1\nb:\n  c: 1\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("parse names a block scalar instead of calling it a nested mapping") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   try {
        |     Yaml::parse("a: |\n  text\n")
        |     return "parsed"
        |   } catch e: Yaml.YamlParseException {
        |     return e.getMessage()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(result match {
      case Shell.Success(s: String) => s.contains("Block scalar") && s.contains("line 1")
      case _ => false
    })
  }

  // Panel block on #2243 at 58ea2aa: a nested sequence was still flattened.
  it("parse rejects a compact sequence under a key instead of reading `- k` as a key") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("items:\n- k: v\n- k: w\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }

  it("parse rejects a sequence nested deeper inside a sequence element") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("- name: a\n  tags:\n    - k: v\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("parse rejects a sequence nested at the element's key column") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Yaml::parse("- name: a\n  tags:\n  - k: v\n")
        |     return -1
        |   } catch e: Yaml.YamlParseException {
        |     return e.getLine()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("still reads a quoted key that starts with a dash as a key") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   return Yaml::parse("\"- x\": 1\n").toString()
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("{- x=1}") == result)
  }
}
