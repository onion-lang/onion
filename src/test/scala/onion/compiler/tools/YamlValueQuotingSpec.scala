package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml::stringify` must quote a String *value* whenever `Yaml::parse` would
 * otherwise read it back as a different type (see docs/reference/stdlib.md's
 * Yaml::stringify section and `YamlKeyQuotingSpec` for the key-side analog).
 *
 * `looksLikeNumber` (the value-quoting guard) is documented as mirroring
 * `parseScalar`'s number detection exactly, but used a looser ad hoc check
 * that quick-rejected any string not starting with a digit or `-`. A
 * leading-dot float like `.5` passes `parseScalar`'s own float regex
 * (`-?\d*\.\d+(...)?`, whose digits before `.` are optional) but was never
 * quoted, so a String value of `.5` silently came back as a Double on parse.
 */
class YamlValueQuotingSpec extends AbstractShellSpec {
  describe("Yaml::stringify value quoting") {
    it("round-trips a leading-dot float-looking String value") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["field": ".5"]
          |    val y = Yaml::stringify(m)
          |    val back = (Yaml::parse(y) as Map)
          |    val v = back.get("field")
          |    if !(v is String) { return "corrupted: " + v }
          |    if (v as String) != ".5" { return "wrong value: " + v }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("round-trips a negative leading-dot float-looking String value") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["field": "-.5"]
          |    val y = Yaml::stringify(m)
          |    val back = (Yaml::parse(y) as Map)
          |    val v = back.get("field")
          |    if !(v is String) { return "corrupted: " + v }
          |    if (v as String) != "-.5" { return "wrong value: " + v }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("round-trips a leading-dot float-looking String value with an exponent") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["field": ".5e3"]
          |    val y = Yaml::stringify(m)
          |    val back = (Yaml::parse(y) as Map)
          |    val v = back.get("field")
          |    if !(v is String) { return "corrupted: " + v }
          |    if (v as String) != ".5e3" { return "wrong value: " + v }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("still round-trips an ordinary numeric-looking String value") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["field": "3.14"]
          |    val y = Yaml::stringify(m)
          |    val back = (Yaml::parse(y) as Map)
          |    val v = back.get("field")
          |    if !(v is String) { return "corrupted: " + v }
          |    if (v as String) != "3.14" { return "wrong value: " + v }
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }
}
