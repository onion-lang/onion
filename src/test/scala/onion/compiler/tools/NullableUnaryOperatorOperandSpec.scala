package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand in a unary operator (e.g. `-n` where `n: Int?`, `!b` where
 * `b: Boolean?`, `~n` where `n: Int?`) reported the generic "operator X is not
 * applicable for type ..." (E0001) instead of the null-safety error (E0070) that
 * the equivalent binary operator (`n - 1` where `n: Int?`) already reports on a
 * nullable operand, since both unbox/dereference the operand the same way.
 *
 * None of `OperatorTyping.typeUnaryNumeric` (`-`/`+`), `.typeUnaryIntegral` (`~`),
 * or `.typeUnaryBoolean` (`!`) special-cased a `NullableType` operand before
 * falling into the generic `INCOMPATIBLE_OPERAND_TYPE` report, unlike the binary
 * operators, which already do via `reportNullableOperandIfPresent`. Fixed by
 * reusing that same helper for a single-operand call from each of the three
 * unary sites.
 */
class NullableUnaryOperatorOperandSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def program(decl: String, expr: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    $decl
       |    val r = $expr
       |    return 0
       |  }
       |}
       |""".stripMargin

  private val cases = Seq(
    "val n: Int? = null" -> "-n",
    "val n: Int? = null" -> "+n",
    "val n: Int? = null" -> "~n",
    "val b: Boolean? = null" -> "!b"
  )

  cases.foreach { case (decl, expr) =>
    it(s"reports E0070, not the generic E0001 fallback, for a nullable operand in `$expr`") {
      val codes = errorCodes(program(decl, expr))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$expr`")
      assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message for `$expr`: $codes")
    }
  }

  it("rejects a nullable unary operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("val n: Int? = null", "-n"), "None", Array()))
  }

  it("still reports the generic E0001 for a genuinely incompatible, non-nullable unary operand") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val s: String = "x"
        |    val r = -s
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0001"), s"expected E0001 for a non-nullable incompatible operand: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable operand: $codes")
  }

  it("still compiles and runs a unary operator on a non-null, non-nullable operand") {
    assert(Shell.Success("-5") == shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val n: Int = 5
        |    return "" + (-n)
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    ))
  }
}
