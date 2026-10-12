package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml::parse` scalar handling for three real-config shapes it previously missed (#2081):
 * a trailing ` #comment` kept as part of the value instead of stripped, a single-quoted
 * `'...'` string left with its quotes instead of unquoted, and `~` read back as the literal
 * string `"~"` instead of `null`. A comment or quote char inside a quoted value must still
 * survive untouched.
 */
class YamlCommentQuoteTildeSpec extends AbstractShellSpec {
  describe("Yaml::parse trailing comments, single-quoted strings, and ~") {
    it("strips a trailing # comment and still infers the value's real type") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("a: 1 # note\n") as Map)
          |    val v = m.get("a")
          |    if v.getClass().getName() != "java.lang.Long" { return "wrong type: " + v }
          |    return v.toString()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("unquotes a single-quoted string value") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("b: 'hello'\n") as Map)
          |    return (m.get("b") as String)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("keeps a # inside a single-quoted value, not treating it as a comment") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("b: 'q # no'\n") as Map)
          |    return (m.get("b") as String)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("q # no") == result)
    }

    it("unescapes a doubled '' inside a single-quoted value to one literal '") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("b: 'it''s here'\n") as Map)
          |    return (m.get("b") as String)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("it's here") == result)
    }

    it("parses ~ as null") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("c: ~\n") as Map)
          |    if m.get("c") == null { return "null" } else { return "not-null: " + m.get("c") }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("still keeps a trailing comment, a # inside quotes, and ~ unquoted together, matching the filed repro") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("a: 1 # note\nb: 'q # no'\nc: ~\n") as Map)
          |    return m.get("a") + "|" + m.get("b") + "|" + (if m.get("c") == null { "null" } else { "not-null" })
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1|q # no|null") == result)
    }

    it("still quotes a double-quoted string containing a # unchanged") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = (Yaml::parse("b: \"q # no\"\n") as Map)
          |    return (m.get("b") as String)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("q # no") == result)
    }
  }
}
