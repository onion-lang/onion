package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand (e.g. `n: Int?`) used as an array size in `new T[n]`
 * reported the generic "type Int is expected, but type Int? is used"
 * (E0000) instead of the null-safety error (E0070) that every other
 * dereference-like use of a nullable value (member access, indexing,
 * operators, conditions, foreach, destructuring, post-update) already
 * reports.
 *
 * `ConstructionTyping.typeNewArray` unboxed each size argument with
 * `Boxing.tryUnboxToInteger` and, on failure, fell straight into a generic
 * `INCOMPATIBLE_TYPE` report against the original (still-nullable) type,
 * unlike `typeIndexing` right above it, which already special-cases
 * `NullableType` before its own generic indexing-target report. Fixed by
 * reporting `NULLABLE_MEMBER_ACCESS` for a `NullableType` size argument
 * before attempting to unbox it.
 */
class NullableArraySizeSpec extends AbstractShellSpec {
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
       |    val arr = $expr
       |    return 0
       |  }
       |}
       |""".stripMargin

  it("reports E0070, not the generic E0000 fallback, for a nullable size in `new Int[n]`") {
    val codes = errorCodes(program("new Int[n]"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("reports E0070 for a nullable size in a 2D array, `new Int[n][2]`") {
    val codes = errorCodes(program("new Int[n][2]"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("rejects a nullable array size at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("new Int[n]"), "None", Array()))
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable array size") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val s: String = "3"
        |    val arr = new Int[s]
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible size: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable size: $codes")
  }
}
