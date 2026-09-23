package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A `catch` clause whose declared exception type is nullable (`catch e: E?`
 * where `E?` is a `NullableType`) reported the generic "type Throwable is
 * expected, but type E? is used" (E0000) instead of the null-safety error
 * (E0070, NULLABLE_MEMBER_ACCESS) that every other nullable-type misuse
 * (member access, indexing, operators, conditions, array size, foreach,
 * destructuring, throw, try-with-resources, range bounds, select scrutinee,
 * assignment) already reports.
 *
 * Unlike those other forms, there is no runtime value to null-check here:
 * a caught exception is never null, so the fix is to drop the `?` from the
 * catch clause's declared type rather than to null-check a value. Both the
 * expression-position try (`TryExpressionTyping.typeTryExpression`) and the
 * statement-position try (`BlockElementLowering.translate`'s
 * `AST.TryExpression` case) checked `TypeRules.isSuperType(expected, argType)`
 * directly and, on failure, fell straight into a generic `INCOMPATIBLE_TYPE`
 * report -- `isSuperType` always returns `false` when the right-hand side is
 * a bare `NullableType`, so a nullable catch type can never pass the check
 * even when its inner type does extend Throwable. Fixed by special-casing a
 * `NullableType` catch argument up front, before the `isSuperType` check, in
 * both code paths, and reporting `NULLABLE_MEMBER_ACCESS`.
 */
class NullableCatchTypeSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def statementProgram(catchType: String): String =
    s"""
       |import { java.lang.Exception; }
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    try {
       |      throw new Exception("boom")
       |    } catch e: $catchType {
       |    }
       |    return 0
       |  }
       |}
       |""".stripMargin

  private def expressionProgram(catchType: String): String =
    s"""
       |import { java.lang.Exception; }
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    val x: Int = try { 1 } catch e: $catchType { 0 }
       |    return x
       |  }
       |}
       |""".stripMargin

  it("reports E0070, not the generic E0000 fallback, for a nullable catch type (statement position)") {
    val codes = errorCodes(statementProgram("Exception?"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("reports E0070, not the generic E0000 fallback, for a nullable catch type (expression position)") {
    val codes = errorCodes(expressionProgram("Exception?"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable catch type (statement position)") {
    val codes = errorCodes(statementProgram("String"))
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible catch type: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable catch type: $codes")
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable catch type (expression position)") {
    val codes = errorCodes(expressionProgram("String"))
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible catch type: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable catch type: $codes")
  }

  it("still compiles cleanly for a genuinely non-nullable catch type (statement position)") {
    val codes = errorCodes(statementProgram("Exception"))
    assert(codes.isEmpty, s"expected no errors for a non-nullable catch type: $codes")
  }

  it("still compiles cleanly for a genuinely non-nullable catch type (expression position)") {
    val codes = errorCodes(expressionProgram("Exception"))
    assert(codes.isEmpty, s"expected no errors for a non-nullable catch type: $codes")
  }
}
