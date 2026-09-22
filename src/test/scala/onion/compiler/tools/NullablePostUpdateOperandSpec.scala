package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable operand in post-increment/post-decrement (`n++`/`n--` where
 * `n: Int?`, with no safe-navigation syntax involved) reported the generic
 * "operator X is not applicable for type ..." (E0001) instead of the
 * null-safety error (E0070) that every sibling operator form already
 * reports on a nullable operand: the unary operators `-n`/`+n`/`~n`/`!b`
 * (`NullableUnaryOperandSpec`, via `reportNullableUnaryOperandIfPresent`),
 * the binary operators `n - 1` etc. (`reportNullableOperandIfPresent`), and
 * post-increment/decrement *through safe navigation* specifically
 * (`obj?.field++`, `SafeNavigationPostUpdateSpec`, which reports E0028
 * instead since `?.`/`?[` have no sensible meaning as an update target).
 *
 * `OperatorTyping.typePostUpdate` special-cased the *syntax* of the operand
 * (`AST.SafeMemberSelection`/`AST.SafeIndexing`) for the E0028 case, but for
 * every other operand syntax (a plain nullable local/field) whose *type* is
 * `NullableType`, it fell straight into the generic
 * `operand.isBasicType`/`hasNumericType` check and reported E0001, never
 * consulting `reportNullableUnaryOperandIfPresent` the way every other
 * operator already does.
 */
class NullablePostUpdateOperandSpec extends AbstractShellSpec {
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
       |    $expr
       |    return 0
       |  }
       |}
       |""".stripMargin

  private val forms = Seq("n++", "n--")

  forms.foreach { expr =>
    it(s"reports E0070, not the generic E0001 fallback, for a nullable operand in `$expr`") {
      val codes = errorCodes(program("var n: Int? = null", expr))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$expr`")
      assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message for `$expr`: $codes")
    }
  }

  it("rejects a nullable post-update operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("var n: Int? = null", "n++"), "None", Array()))
  }

  it("still reports the generic E0001 for a genuinely incompatible, non-nullable post-update operand") {
    val codes = errorCodes(program("var s: String = \"x\"", "s++"))
    assert(codes.contains("E0001"), s"expected E0001 for a non-nullable incompatible operand: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable operand: $codes")
  }

  it("still reports E0028, not E0070, for post-increment through safe navigation") {
    val src =
      """
        |class Box {
        |public:
        |  var count: Int
        |  def this { count = 0 }
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Box? = null
        |    b?.count++
        |    return 0
        |  }
        |}
        |""".stripMargin
    val codes = errorCodes(src)
    assert(codes.contains("E0028"), s"expected E0028 in $codes")
    assert(!codes.contains("E0070"), s"safe-navigation post-update should keep its own E0028 diagnostic, not E0070: $codes")
  }
}
