package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml::stringify` quotes a String *value* that would be misread on parse-back
 * (contains `:`, `#`, a newline, or looks like a number/boolean — see
 * docs/reference/stdlib.md's Yaml::stringify section) but historically left the
 * map *key* unquoted. A key containing `": "` collided with the `key: value`
 * separator the parser looks for, silently losing part of the key into the value
 * instead of failing — the same "misread on parse-back" hazard the value side
 * already guards against.
 */
class YamlKeyQuotingSpec extends AbstractShellSpec {
  describe("Yaml::stringify key quoting") {
    it("round-trips a key containing a colon-space delimiter") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a: b": 1L]
          |    val y = Yaml::stringify(m)
          |    val back = (Yaml::parse(y) as Map)
          |    if !back.containsKey("a: b") { return "key lost" }
          |    val v = (back.get("a: b") as Long)
          |    if v != 1L { return "value corrupted: " + v }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("round-trips a key with leading/trailing whitespace") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = [" pad ": 1L]
          |    val y = Yaml::stringify(m)
          |    val back = (Yaml::parse(y) as Map)
          |    if !back.containsKey(" pad ") { return "key lost" }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("still renders a plain identifier-like key unquoted") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["name": "Alice"]
          |    return Yaml::stringify(m)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("name: Alice\n") == result)
    }
  }
}
