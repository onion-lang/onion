package onion.compiler.tools

import onion.tools.Shell

/**
 * A `select` over a sealed type must be exhaustive. Subtypes declared with an
 * ordinary `class` (not only `record`) are registered, so a missing case is a
 * compile error (E0042) rather than a silent `null` at runtime.
 */
class SealedExhaustivenessSpec extends AbstractShellSpec {
  describe("sealed exhaustiveness") {
    it("rejects a non-exhaustive select over a sealed class with class subtypes") {
      val r = shell.run(
        """
          |sealed class Expr {}
          |class Num : Expr { public: def this { } }
          |class Add : Expr { public: def this { } }
          |def kind(e: Expr): String { return select e { case n is Num: "num" } }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("accepts an exhaustive select over a sealed class") {
      val r = shell.run(
        """
          |sealed class Expr {}
          |class Num : Expr { public: def this { } }
          |class Add : Expr { public: def this { } }
          |def kind(e: Expr): String {
          |  return select e {
          |    case n is Num: "num"
          |    case a is Add: "add"
          |  }
          |}
          |def main(args: String[]): String { return kind(new Add()) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("add") == r)
    }
    it("rejects a non-exhaustive select over a sealed interface with class subtypes") {
      val r = shell.run(
        """
          |sealed interface Shape {}
          |class Circle <: Shape { public: def this { } }
          |class Square <: Shape { public: def this { } }
          |def name(s: Shape): String { return select s { case c is Circle: "circle" } }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
