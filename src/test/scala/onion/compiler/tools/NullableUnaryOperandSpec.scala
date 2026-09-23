package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand in a unary operator (e.g. `-n` where `n: Int?`, `~n`
 * where `n: Int?`, or `!b` where `b: Boolean?`) reported the generic
 * "operator X is not applicable for type ..." (E0001) instead of the
 * null-safety error (E0070) that the equivalent binary operators
 * (`n - 1`, fixed in v0.97.0 via `OperatorTyping.reportNullableOperandIfPresent`)
 * already report on a nullable operand.
 *
 * None of `OperatorTyping.typeUnaryNumeric` (`-`/`+`), `.typeUnaryIntegral`
 * (`~`), or `.typeUnaryBoolean` (`!`) special-cased a `NullableType` operand
 * before falling into the generic `INCOMPATIBLE_OPERAND_TYPE` report, unlike
 * their binary siblings.
 */
class NullableUnaryOperandSpec extends AbstractShellSpec {
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

  private val numericAndIntegral = Seq(
    "val n: Int? = null" -> "-n",
    "val n: Int? = null" -> "+n",
    "val n: Int? = null" -> "~n"
  )

  numericAndIntegral.foreach { case (decl, expr) =>
    it(s"reports E0070, not the generic E0001 fallback, for a nullable operand in `$expr`") {
      val codes = errorCodes(program(decl, expr))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$expr`")
      assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message for `$expr`: $codes")
    }
  }

  it("reports E0070, not the generic E0001 fallback, for a nullable operand in `!b`") {
    val codes = errorCodes(program("val b: Boolean? = null", "!b"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message: $codes")
  }

  it("rejects a nullable unary operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("val n: Int? = null", "-n"), "None", Array()))
  }

  it("still reports the generic E0001 for a genuinely incompatible, non-nullable unary operand") {
    val codes = errorCodes(program("val s: String = \"x\"", "-s"))
    assert(codes.contains("E0001"), s"expected E0001 for a non-nullable incompatible operand: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable operand: $codes")
  }
}
