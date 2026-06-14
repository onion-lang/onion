package onion.compiler.tools

import onion.tools.Shell

/**
 * `==` on a statically-nullable receiver is null-safe value equality (via
 * java.util.Objects.equals), matching Kotlin: both-null is equal, one-null is not,
 * otherwise structural equals. Previously a nullable receiver fell back to reference
 * comparison, so `fromJson(s) == expected` was surprisingly false without a null-check.
 */
class NullableEqualitySpec extends AbstractShellSpec {
  describe("== on nullable receivers") {
    it("does value equality without a null-check") {
      val result = shell.run(
        """
          |record R(a: String, b: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = new R("ko", 3)
          |    val y = R::fromJson(R::toJson(x))
          |    if y == x { return "equal" } else { return "not" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("equal") == result)
    }

    it("treats one-null as not equal") {
      val result = shell.run(
        """
          |record R(a: String, b: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = new R("ko", 3)
          |    val bad = R::fromJson("garbage")
          |    if bad == x { return "equal" } else { return "not" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("not") == result)
    }

    it("treats both-null as equal") {
      val result = shell.run(
        """
          |record R(a: String, b: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = R::fromJson("bad-one")
          |    val q = R::fromJson("bad-two")
          |    if p == q { return "equal" } else { return "not" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("equal") == result)
    }

    it("nullable != reflects value (in)equality") {
      val result = shell.run(
        """
          |record R(a: String, b: Int) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = new R("ko", 3)
          |    val y = R::fromJson(R::toJson(x))
          |    val bad = R::fromJson("garbage")
          |    val eqY = if y != x { "Y-ne" } else { "Y-eq" }
          |    val eqB = if bad != x { "B-ne" } else { "B-eq" }
          |    return eqY + "," + eqB
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Y-eq,B-ne") == result)
    }

    it("works for a nullable String operand") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v: String? = "value"
          |    if v == "value" { return "equal" } else { return "not" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("equal") == result)
    }
  }
}
