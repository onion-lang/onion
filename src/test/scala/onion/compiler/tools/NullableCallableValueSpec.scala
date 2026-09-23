package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Calling a nullable callable value directly (`f(x)` where `f: Function1[A, B]?`,
 * never null-checked) reported the generic `E0005` (`METHOD_NOT_FOUND`, "a method
 * applicable for ...f(...) is not found. Check spelling and argument types.")
 * instead of the null-safety error (E0070, NULLABLE_MEMBER_ACCESS) that the
 * equivalent explicit call (`f.call(x)`) already reports on a nullable receiver.
 *
 * `CallableValueCallSupport.resolveCallableValue` matched the local/field type
 * against `case targetType: ObjectType => ...` and fell straight to `case _ =>
 * None` for anything else, including a `NullableType` wrapping a perfectly
 * callable inner type -- `NullableType` is not an `ObjectType`. Returning `None`
 * (rather than reporting the nullability) let `UnqualifiedMethodCallSupport` fall
 * through to its final `reportMethodNotFound`, which reports E0005 instead.
 * Fixed by special-casing a `NullableType` local/field whose inner type has a
 * `call` method up front, reporting `NULLABLE_MEMBER_ACCESS`, mirroring
 * `MethodTargetTypingSupport.normalizeMethodCallTarget`'s explicit-call-target
 * handling of the same case.
 */
class NullableCallableValueSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def programWithLocal(callExpr: String): String =
    s"""
       |class Test {
       |public:
       |  static def main(args: String[]): Integer {
       |    val f: Function1[Integer, Integer]? = (x: Integer) -> x + 1
       |    return $callExpr
       |  }
       |}
       |""".stripMargin

  it("reports E0070, not the generic E0005 fallback, for a nullable local callable value") {
    val codes = errorCodes(programWithLocal("f(41)"))
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0005"), s"must not fall back to the generic E0005 message: $codes")
  }

  it("rejects a nullable local callable value at runtime-failure granularity via Shell.Failure(-1)") {
    assert(Shell.Failure(-1) == shell.run(programWithLocal("f(41)"), "None", Array()))
  }

  it("reports E0070, not the generic E0005 fallback, for a nullable field callable value") {
    val codes = errorCodes(
      """
        |class Test {
        |  var f: Function1[Integer, Integer]?
        |public:
        |  def this { this.f = (x: Integer) -> x + 1 }
        |  def go: Integer = f(41)
        |  static def main(args: String[]): Integer = new Test().go
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0005"), s"must not fall back to the generic E0005 message: $codes")
  }

  it("still reports the generic E0005 for a genuinely undefined bare call") {
    val codes = errorCodes(programWithLocal("thisIsNotDefined(41)"))
    assert(codes.contains("E0005"), s"expected E0005 for a genuinely undefined bare call: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for an unrelated undefined call: $codes")
  }

  it("still compiles and calls a genuinely non-null callable value typed as nullable") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Integer {
        |    val f: Function1[Integer, Integer]? = (x: Integer) -> x + 1
        |    val g: Function1[Integer, Integer] = f!!
        |    return g(41)
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors after `!!`-asserting non-null: $codes")
  }
}
