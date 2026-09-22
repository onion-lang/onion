package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable resource initializer in try-with-resources (`try (val r = res) { }`
 * where `res: R?` and `R` conforms `AutoCloseable`) reported the generic
 * "type AutoCloseable is expected, but type R? is used" (E0000) instead of
 * the null-safety error (E0070, NULLABLE_MEMBER_ACCESS) that every other
 * dereference-like use of a nullable value (member access, indexing,
 * operators, conditions, array size, foreach, destructuring) already
 * reports.
 *
 * `TryExpressionTyping.typeTryExpression` checked
 * `TypeRules.isSuperType(autoCloseable, resourceType)` directly and, on
 * failure, fell straight into a generic `INCOMPATIBLE_TYPE` report against
 * the original (still-nullable) resource type -- `isSuperType` always
 * returns `false` when the right-hand side is a bare `NullableType`
 * (`T <- T?` requires an explicit unwrap), so a nullable resource can never
 * pass the check, even when its inner type does implement `AutoCloseable`.
 * Fixed by special-casing a `NullableType` resource type up front, before
 * the `isSuperType` check, and reporting `NULLABLE_MEMBER_ACCESS`.
 */
class NullableTryResourceSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val resourceClass =
    """
      |import { java.lang.AutoCloseable; }
      |class Res conforms AutoCloseable {
      |public:
      |  def this { }
      |  def close(): void { }
      |}
      |""".stripMargin

  private def program(resourceExpr: String): String =
    resourceClass +
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |    val r: Res? = null
         |    try (val x = $resourceExpr) {
         |    }
         |    return 0
         |  }
         |}
         |""".stripMargin

  it("reports E0070, not the generic E0000 fallback, for a nullable try-with-resources initializer") {
    val codes = errorCodes(program("r"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("rejects a nullable resource at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(program("r"), "None", Array()))
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable resource type") {
    val codes = errorCodes(
      resourceClass +
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "3"
          |    try (val x = s) {
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
    )
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible resource type: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable resource type: $codes")
  }

  it("still compiles and closes a genuinely non-null resource typed as nullable") {
    val codes = errorCodes(
      resourceClass +
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Res? = new Res()
          |    try (val x = r!!) {
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors after `!!`-asserting non-null: $codes")
  }
}
