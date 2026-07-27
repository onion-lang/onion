package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Dereferencing a nullable value's field directly (`x.length` where `x: String?`)
 * reports a clean null-safety error (E0070) that points at `?.`/`?:`/`!!`/a null
 * check — matching the method-call path — instead of the misleading
 * "Object expected" (E0000) the generic fallback used to produce.
 */
class NullableMemberAccessSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("rejects direct field access on a nullable value") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val x: String? = "abc"
        |   return x.length
        | }
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not the generic E0000 fallback, for direct field access on a nullable value") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val x: String? = "abc"
        |    return x.length
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("accepts safe field access on a nullable value") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val x: String? = "abc"
        |   return x?.length ?: 0
        | }
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(3) == result)
  }
}
