package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for issue #306: an INFERRED lambda parameter must take its type
 * from the SAM's PARAMETER slot, in the same (primitive) form an explicit
 * parameter would.
 *
 * For `apply(f: Function1[Int, Long], x: Int): Long`, both
 *   apply((n: Int) -> (n as Long), 5)   // explicit
 *   apply((n)      -> (n as Long), 5)    // inferred
 * must compile and return 5. The generic SAM slot `Function1[Int, Long]` erases
 * its parameter to the boxed wrapper `java.lang.Integer`; the inferred parameter
 * used to bind as that boxed `Integer` (while the explicit form binds primitive
 * `Int`), so `n as Long` on a `java.lang.Integer` misfired as
 * "型 Long が期待... 型 Int を使用" (E0000) with the caret on `n`. The inferred
 * parameter now unboxes a boxed-primitive SAM slot to the primitive, matching
 * the explicit-parameter behavior. Reference SAM parameters are untouched.
 *
 * Error assertions use Shell.Failure(-1) / error CODES rather than localized
 * message text, since release CI runs in an English locale.
 */
class InferredLambdaParamSlotSpec extends AbstractShellSpec {

  describe("inferred lambda parameter takes the SAM PARAMETER slot (#306)") {
    it("compiles an inferred param whose body result type differs (static call)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def apply(f: Function1[Int, Long], x: Int): Long = f.call(x)
          |  static def main(args: String[]): Long = Main::apply((n) -> (n as Long), 5)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5L) == result)
    }

    it("still compiles the equivalent explicit-typed parameter (static call)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def apply(f: Function1[Int, Long], x: Int): Long = f.call(x)
          |  static def main(args: String[]): Long = Main::apply((n: Int) -> (n as Long), 5)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5L) == result)
    }

    it("compiles an inferred param for an instance-method SAM parameter") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  def apply(f: Function1[Int, Long], x: Int): Long = f.call(x)
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Long = new Box().apply((n) -> (n as Long), 7)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(7L) == result)
    }

    it("compiles an inferred param for an unqualified top-level function call") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def apply(f: Function1[Int, Long], x: Int): Long = f.call(x)
          |  static def main(args: String[]): Long = apply((n) -> (n as Long), 9)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(9L) == result)
    }

    it("uses the primitive parameter directly (arithmetic on an inferred Int param)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def apply(f: Function1[Int, Int], x: Int): Int = f.call(x)
          |  static def main(args: String[]): Integer = Main::apply((n) -> n + 100, 5)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(105) == result)
    }

    it("handles a Double primitive SAM parameter inferred") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def apply(f: Function1[Double, Double], x: Double): Double = f.call(x)
          |  static def main(args: String[]): Double = Main::apply((d) -> d * 2.0, 3.5)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(7.0) == result)
    }
  }

  describe("reference SAM parameters and genuine mismatches are unaffected") {
    it("still binds a reference-typed inferred parameter") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def apply(f: Function1[String, Int], s: String): Int = f.call(s)
          |  static def main(args: String[]): Integer = Main::apply((s) -> s.length(), "hello")
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("still rejects an explicit parameter that mismatches the SAM (clean failure)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): void {
          |    val f: Function1[Int, Int] = (x: String) -> x
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
