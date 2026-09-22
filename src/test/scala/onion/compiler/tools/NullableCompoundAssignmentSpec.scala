package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Regression coverage locking in that every compound-assignment operator
 * (`+= -= *= /= %= &= |= ^= <<= >>= >>>=`) already reports the null-safety
 * error (`E0070`, `NULLABLE_MEMBER_ACCESS`) -- not the generic `E0001`
 * ("operator X is not applicable for type ...") -- when its target is a
 * plain nullable operand (`n: Int?`, no safe-navigation syntax involved).
 *
 * `SimpleExpressionTypingSupport.typeBinaryAssignment` desugars `n op= v`
 * into the equivalent binary expression (`n = n op v`) and reuses the same
 * `OperatorTyping`/`AdditionTyping` report sites that the plain binary
 * operators go through (`NullableOperatorOperandSpec`, `NullableShiftOperandSpec`),
 * so this already works correctly; unlike those sibling operator forms
 * (unary, binary, shift, post-update), no spec exercised the compound-assignment
 * desugaring path directly on a plain nullable local. This spec closes that gap
 * and also covers a compound assignment through a non-nullable receiver whose
 * *field* is itself declared nullable (distinct from `NullableMemberAssignmentTargetSpec`,
 * which covers a nullable *receiver* with a non-nullable field).
 */
class NullableCompoundAssignmentSpec extends AbstractShellSpec {
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

  private val forms = Seq("n += 1", "n -= 1", "n *= 1", "n /= 1", "n %= 1",
    "n &= 1", "n |= 1", "n ^= 1", "n <<= 1", "n >>= 1", "n >>>= 1")

  forms.foreach { expr =>
    it(s"reports E0070, not the generic E0001 fallback, for a nullable operand in `$expr`") {
      val codes = errorCodes(program("var n: Int? = null", expr))
      assert(codes.contains("E0070"), s"expected E0070 in $codes for `$expr`")
      assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message for `$expr`: $codes")
    }
  }

  it("rejects a nullable compound-assignment operand at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("var n: Int? = null", "n += 1"), "None", Array()))
  }

  it("still reports the generic E0001 for a genuinely incompatible, non-nullable compound-assignment operand") {
    val codes = errorCodes(program("var s: String = \"x\"", "s -= 1"))
    assert(codes.contains("E0001"), s"expected E0001 for a non-nullable incompatible operand: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable operand: $codes")
  }

  it("reports E0070 for compound assignment to a field that is itself declared nullable, through a non-nullable receiver") {
    val src =
      """
        |class Box {
        |public:
        |  var count: Int?
        |  def this { count = 1 }
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val b: Box = new Box()
        |    b.count += 5
        |    return 0
        |  }
        |}
        |""".stripMargin
    val codes = errorCodes(src)
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0001"), s"must not fall back to the generic E0001 message: $codes")
  }
}
