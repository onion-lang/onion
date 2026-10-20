package onion.compiler.tools

import onion.tools.Shell

/**
 * `<`, `<=`, `>`, `>=` accepted only numbers, so `"abc" < "abd"` or comparing two dates was
 * E0001 and needed `a.compareTo(b) < 0`. For a non-null operand with `compareTo` (any
 * `Comparable`: String, BigDecimal, LocalDate, a user class conforming to Comparable), the
 * operators now mean `a.compareTo(b) < 0` etc., as in Kotlin. Numbers are unchanged.
 */
class ComparableOperatorSpec extends AbstractShellSpec {
  private def eval(body: String, prelude: String = ""): Shell.Result =
    shell.run(
      s"""
         |$prelude
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("comparison operators on Comparable values") {
    it("compares Strings") {
      assert(eval("""return "" + ("abc" < "abd") + ("b" <= "a") + ("b" > "a") + ("a" >= "a")""") ==
        Shell.Success("truefalsetruetrue"))
    }
    it("compares java.time dates") {
      assert(eval("""val a = java.time.LocalDate::of(2026, 1, 2)
                    |val b = java.time.LocalDate::of(2026, 3, 4)
                    |return "" + (a < b) + (b < a)""".stripMargin) == Shell.Success("truefalse"))
    }
    it("compares BigDecimal by value") {
      assert(eval("""return "" + (new java.math.BigDecimal("1.50") >= new java.math.BigDecimal("1.5"))""") ==
        Shell.Success("true"))
    }
    it("compares a user class conforming to Comparable") {
      assert(eval("""return "" + (new V(1) < new V(2))""",
        """class V(val n: Int) conforms Comparable[V] {
          |public:
          |  def compareTo(o: V): Int = n - o.n
          |}""".stripMargin) == Shell.Success("true"))
    }
    it("works in a filter lambda") {
      assert(eval("""return "" + ["pear", "apple", "fig"].filter { w -> w < "g" }""") == Shell.Success("[apple, fig]"))
    }
    it("leaves numeric comparison unchanged") {
      assert(eval("""return "" + (1 < 2) + (2.5 >= 3)""") == Shell.Success("truefalse"))
    }
    it("still rejects a non-Comparable operand") {
      assert(eval("""return "" + (new Object() < new Object())""") == Shell.Failure(-1))
    }
    it("still rejects a nullable operand") {
      assert(eval("""val s: String? = "a"
                    |return "" + (s < "b")""".stripMargin) == Shell.Failure(-1))
    }
  }
}
