package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `throw expr` where `expr: E?` (a nullable exception type, never null-checked)
 * reported the generic "type java.lang.Throwable is expected, but type E? is
 * used" (E0000) instead of the null-safety error (E0070, NULLABLE_MEMBER_ACCESS)
 * that every other dereference-like use of a nullable value (member access,
 * indexing, operators, conditions, array size, try-with-resources, foreach,
 * destructuring) already reports.
 *
 * `BlockElementLowering.translate`'s `AST.ThrowExpression` case checked
 * `TypeRules.isSuperType(expected, detected)` directly and, on failure, fell
 * straight into a generic `INCOMPATIBLE_TYPE` report against the original
 * (still-nullable) detected type -- `isSuperType` always returns `false` when
 * the right-hand side is a bare `NullableType`, so a nullable throw operand
 * can never pass the check, even when its inner type does extend Throwable.
 * Fixed by special-casing a `NullableType` operand up front, before the
 * `isSuperType` check, and reporting `NULLABLE_MEMBER_ACCESS`.
 */
class NullableThrowSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def program(throwExpr: String): String =
    s"""
       |import { java.lang.Exception; }
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    val e: Exception? = null
       |    throw $throwExpr
       |    return 0
       |  }
       |}
       |""".stripMargin

  it("reports E0070, not the generic E0000 fallback, for a nullable throw operand") {
    val codes = errorCodes(program("e"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("rejects a nullable throw operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("e"), "None", Array()))
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable throw operand") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val s: String = "3"
        |    throw s
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible throw operand: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable throw operand: $codes")
  }

  it("still compiles and throws a genuinely non-null operand typed as nullable") {
    val codes = errorCodes(
      """
        |import { java.lang.Exception; }
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val e: Exception? = new Exception("boom")
        |    throw e!!
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors after `!!`-asserting non-null: $codes")
  }
}
