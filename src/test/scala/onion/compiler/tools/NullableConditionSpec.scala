package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable `Boolean?` condition (`if b { }`, `while b { }`, `do { } while b`,
 * a `for` condition, or a `select` guard's `when b`) reported the generic
 * "type Boolean is expected, but type Boolean? is used" (E0000) instead of
 * the null-safety error (E0070) that every other nullable-operand form
 * (unary/binary operators, indexing, member access, foreach collections,
 * destructuring) already reports.
 *
 * `ControlExpressionTyping.ensureBoolean` (used by `if`-as-expression and by
 * `select` guards via `SelectExpressionTyping.ensureBoolean`) and
 * `BlockElementLowering.ensureBooleanCondition` (used by `if`-as-statement,
 * `while`, `do-while`, and `for`) both delegate to the shared
 * `TypeCheckingHelpers.ensureBoolean`, but their `reportError` callback
 * reported `INCOMPATIBLE_TYPE` unconditionally, never checking for a
 * `NullableType` operand the way `OperatorTyping`'s callers do.
 */
class NullableConditionSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def program(decl: String, stmt: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    $decl
       |    $stmt
       |    return 0
       |  }
       |}
       |""".stripMargin

  private val forms = Seq(
    "if" -> "if b { IO::println(\"x\") }",
    "if-else" -> "if b { IO::println(\"x\") } else { IO::println(\"y\") }",
    "while" -> "while b { break }",
    "do-while" -> "do { break } while b",
    "for" -> "for var i: Int = 0; b; i++ { break }"
  )

  forms.foreach { case (name, stmt) =>
    it(s"reports E0070, not the generic E0000 fallback, for a nullable `Boolean?` condition in `$name`") {
      val codes = errorCodes(program("val b: Boolean? = null", stmt))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$name`")
      assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message for `$name`: $codes")
    }
  }

  it("reports E0070, not the generic E0000 fallback, for a nullable `Boolean?` select guard") {
    val src = program(
      "val n: Int = 1\n    val b: Boolean? = null",
      "select n {\n" +
        "      case v when b: IO::println(\"x\")\n" +
        "      else: IO::println(\"y\")\n" +
        "    }"
    )
    val codes = errorCodes(src)
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("rejects a nullable `if` condition at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("val b: Boolean? = null", "if b { IO::println(\"x\") }"), "None", Array()))
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable, non-boolean condition") {
    val codes = errorCodes(program("val n: Int = 1", "if n { IO::println(\"x\") }"))
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible condition: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable condition: $codes")
  }
}
