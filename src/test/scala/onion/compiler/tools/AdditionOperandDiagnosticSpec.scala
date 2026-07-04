package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression spec for issue #265.
 *
 * `Int + Boolean` previously fell back to string concatenation, inferring the
 * whole expression to String, so an assignment to Int surfaced a confusing
 * "String used" mismatch that hid the offending Boolean operand. When numeric
 * addition fails and NEITHER operand is a String, the compiler now reports an
 * invalid-operand-for-'+' diagnostic instead of silently concatenating.
 *
 * Intentional concatenation (at least one String operand) must still work.
 */
class AdditionOperandDiagnosticSpec extends AbstractShellSpec {
  describe("addition operand diagnostics (#265)") {
    it("rejects Int + Boolean instead of silently concatenating to String") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 5;
          |    val b: Boolean = true;
          |    val r: Int = x + b;
          |    return r;
          |  }
          |}
        """.stripMargin,
        "IntPlusBoolean.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects Boolean + Boolean (neither operand is a String)") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Boolean = true;
          |    val b: Boolean = false;
          |    return a + b;
          |  }
          |}
        """.stripMargin,
        "BoolPlusBool.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still concatenates when the left operand is a String literal") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Boolean = true;
          |    return "flag=" + b;
          |  }
          |}
        """.stripMargin,
        "StringPlusBool.on",
        Array()
      )
      assert(Shell.Success("flag=true") == result)
    }

    it("still concatenates when the right operand is a String literal") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 5;
          |    return x + "s";
          |  }
          |}
        """.stripMargin,
        "IntPlusString.on",
        Array()
      )
      assert(Shell.Success("5s") == result)
    }

    it("still adds two numbers") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    return 3 + 4;
          |  }
          |}
        """.stripMargin,
        "IntPlusInt.on",
        Array()
      )
      assert(Shell.Success(7) == result)
    }
  }
}
