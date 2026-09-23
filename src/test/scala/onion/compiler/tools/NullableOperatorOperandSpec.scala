package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand in an arithmetic, comparison, or bitwise binary operator
 * (e.g. `n - 1` where `n: Int?`, or `a < b` where `a: T?`) reported the generic
 * "operator X is not applicable for type ..." (E0001) instead of the null-safety
 * error (E0070) that the equivalent member access (`b.field`), indexing (`b[i]`),
 * and foreach (`foreach x in b`) already report on a nullable receiver.
 *
 * Every one of these operators unboxes/dereferences its operand exactly like
 * those already-fixed forms, but none of the shared binary-operator report sites
 * (`OperatorTyping.processNumericExpression`, `.processComparableExpression`,
 * `.processBitExpression`, `.processShiftExpression`, and
 * `AdditionTyping.tryStringConcatenationOrReport`) special-cased a `NullableType`
 * operand before falling into the generic `INCOMPATIBLE_OPERAND_TYPE` report --
 * unlike `ConstructionTyping.typeIndexing` and
 * `MemberSelectionResolutionSupport.normalizeTarget`, which already do. Fixed by
 * adding `OperatorTyping.reportNullableOperandIfPresent`, consulted up front at
 * each of those report sites (mirroring the existing per-site special-casing
 * pattern rather than a deep refactor), and a new `nullableOperatorOperand`
 * message key (en/ja).
 *
 * String concatenation is unaffected: `"x" + (n: Int?)` never dereferences the
 * nullable operand (it goes through `String.valueOf`, matching Java's
 * `"a" + null == "anull"` semantics), so it must keep compiling and printing
 * "null" rather than erroring.
 */
class NullableOperatorOperandSpec extends AbstractShellSpec {
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

  private val operators = Seq("n + 1", "n - 1", "n * 1", "n / 1", "n % 1", "n < 1", "n > 1", "n <= 1", "n >= 1", "n & 1", "n | 1", "n ^ 1")

  operators.foreach { expr =>
    it(s"reports E0070, not the generic E0001 fallback, for a nullable left operand in `$expr`") {
      val codes = errorCodes(program(expr))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$expr`")
      assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message for `$expr`: $codes")
    }
  }

  it("reports E0070, not the generic E0001 fallback, for a nullable left operand in `+` (user-defined operator dispatch)") {
    val codes = errorCodes(
      """
        |record Vec(x: Int, y: Int) {
        |public:
        |  def plus(o: Vec): Vec = new Vec(x() + o.x(), y() + o.y())
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val a: Vec? = new Vec(1, 2)
        |    val b: Vec = new Vec(3, 4)
        |    val c = a + b
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message: $codes")
  }

  it("rejects a nullable operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("n - 1"), "None", Array()))
  }

  it("still reports the generic E0001 for a genuinely incompatible, non-nullable operand pair") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Boolean = true
        |    val r = b - 1
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0001"), s"expected E0001 for a non-nullable incompatible pair: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable operand: $codes")
  }

  it("still compiles and runs string concatenation with a nullable operand (no dereference needed)") {
    assert(Shell.Success("value: null") == shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val n: Int? = null
        |    return "value: " + n
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    ))
  }
}
