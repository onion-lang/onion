package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml::parse` reads a single-quoted key the way it reads a single-quoted value:
 * the quotes are removed and `''` is one literal `'` (#2403). Before, `'k': 1` gave
 * the key `'k'` with its quotes, so a lookup of `k` silently returned null.
 */
class YamlSingleQuotedKeySpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |import { onion.Yaml; java.util.Map; }
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("Yaml::parse single-quoted keys") {
    it("strips the quotes of a single-quoted key") {
      val result = run(
        """    val m = (Yaml::parse("'k': 1\n") as Map)
          |    if m.containsKey("'k'") { return "quotes kept" }
          |    return "" + m.get("k")""".stripMargin)
      assert(Shell.Success("1") == result)
    }

    it("keeps a tag-like key as text when it is single-quoted") {
      val result = run(
        """    val m = (Yaml::parse("'!!str 5': x\n") as Map)
          |    return "" + m.get("!!str 5")""".stripMargin)
      assert(Shell.Success("x") == result)
    }

    it("reads '' inside a single-quoted key as one quote") {
      val result = run(
        """    val m = (Yaml::parse("'it''s': 2\n") as Map)
          |    return "" + m.get("it's")""".stripMargin)
      assert(Shell.Success("2") == result)
    }

    it("does not split a single-quoted key at a colon inside it") {
      val result = run(
        """    val m = (Yaml::parse("'a: b': 3\n") as Map)
          |    return m.size() + " " + m.get("a: b")""".stripMargin)
      assert(Shell.Success("1 3") == result)
    }

    it("strips single-quoted keys inside a sequence element") {
      val result = run(
        """    val xs = (Yaml::parse("- 'k': 1\n  'j': 2\n") as java.util.List)
          |    val m = (xs.get(0) as Map)
          |    return m.get("k") + " " + m.get("j")""".stripMargin)
      assert(Shell.Success("1 2") == result)
    }

    it("tells a whole-line flow mapping to use block lines or Json, not to quote a value") {
      val result = run(
        """    try {
          |      Yaml::parse("{a: 1, b: 2}\n")
          |      return "parsed"
          |    } catch e: Exception {
          |      return e.getMessage()
          |    }""".stripMargin)
      result match {
        case Shell.Success(msg: String) =>
          assert(!msg.contains("quote the value"), msg)
          assert(msg.contains("key: value"), msg)
          assert(msg.contains("Json"), msg)
        case other => fail(other.toString)
      }
    }
  }
}
