package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand (e.g. `n: Int?`) in a shift operator (`<<`, `>>`, `>>>`)
 * reported the generic "operator X is not applicable for type ..." (E0001)
 * instead of the null-safety error (E0070) that every sibling binary operator
 * (`+ - * / %`, `< > <= >=`, `& | ^`, fixed in `NullableOperatorOperandSpec`)
 * already reports on a nullable operand.
 *
 * `OperatorTyping.processShiftExpression` never consulted
 * `reportNullableOperandIfPresent` before falling into the generic
 * `INCOMPATIBLE_OPERAND_TYPE` report, unlike `processBitExpression` right next
 * to it, which already does. Fixed by adding the same early guard.
 */
class NullableShiftOperandSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def program(expr: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    val n: Int? = null
       |    val r = $expr
       |    return 0
       |  }
       |}
       |""".stripMargin

  private val operators = Seq("n << 1", "n >> 1", "n >>> 1", "1 << n", "1 >> n", "1 >>> n")

  operators.foreach { expr =>
    it(s"reports E0070, not the generic E0001 fallback, for a nullable operand in `$expr`") {
      val codes = errorCodes(program(expr))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$expr`")
      assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message for `$expr`: $codes")
    }
  }

  it("rejects a nullable shift operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("n << 1"), "None", Array()))
  }

  it("still reports the generic E0001 for a genuinely incompatible, non-nullable shift operand pair") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Boolean = true
        |    val r = b << 1
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0001"), s"expected E0001 for a non-nullable incompatible pair: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable operand: $codes")
  }
}
