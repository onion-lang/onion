package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `val (a, b) = expr` destructuring a nullable record (`Point?`, never null-checked)
 * reported the misleading E0047 ("... is not a record type or does not exist")
 * instead of the null-safety error (E0070) that the equivalent member access
 * (`p.x`) and indexing (`b[i]`) already report on a nullable receiver.
 * `BlockElementLowering.translateDestructuring`'s `accessors` helper only matched
 * `case ct: ClassType`, so a `NullableType`-wrapped record fell through to `case _
 * => None`, and the `None` branch reported `NOT_A_RECORD_TYPE` unconditionally --
 * even though the initializer genuinely is a record, just nullable.
 */
class DestructuringNullableSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("rejects destructuring a nullable record") {
    val result = shell.run(
      """
        |record Point(x: Int, y: Int)
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val p: Point? = new Point(1, 2)
        |    val (a, b) = p
        |    return "" + a + "," + b
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Failure(-1) == result)
  }

  it("reports E0070, not the misleading E0047, for destructuring a nullable record") {
    val codes = errorCodes(
      """
        |record Point(x: Int, y: Int)
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val p: Point? = new Point(1, 2)
        |    val (a, b) = p
        |    return "" + a + "," + b
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0047"), s"must not fall back to the misleading E0047 message: $codes")
  }

  it("accepts destructuring after a null check narrows the receiver") {
    val result = shell.run(
      """
        |record Point(x: Int, y: Int)
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val p: Point? = new Point(3, 4)
        |    if p != null {
        |      val (a, b) = p
        |      return "" + a + "," + b
        |    }
        |    return "null"
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("3,4") == result)
  }
}
