package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

class WideMethodContractSpec extends AbstractShellSpec {
  private val names = (0 until 16).map(i => s"a$i")
  private val intParams = names.map(n => s"$n: Int").mkString(", ")
  private val genericParams = names.map(n => s"$n: T").mkString(", ")
  private val actuals = (0 until 16).mkString(", ")
  private val sum = names.mkString(" + ")

  it("implements a wide generic interface with primitive parameters and dispatches through it") {
    val source = s"""
      |interface Wide[T] { def sum($genericParams): Int }
      |class Impl conforms Wide[Int] {
      |public:
      |  def this {}
      |  override def sum($intParams): Int = $sum
      |}
      |def main(args: String[]): Int {
      |  val impl: Wide[Int] = new Impl()
      |  return impl.sum($actuals)
      |}
      |""".stripMargin
    assert(shell.run(source, "WideGeneric.on", Array.empty) == Shell.Success(120))
  }

  it("rejects a wide override whose final parameter does not implement its contract") {
    val wrongParams = names.zipWithIndex.map { (n, i) =>
      s"$n: ${if (i == 15) "String" else "Int"}"
    }.mkString(", ")
    val source = s"""
      |interface Wide { def sum($intParams): Int }
      |class Impl conforms Wide {
      |public:
      |  override def sum($wrongParams): Int = 0
      |}
      |""".stripMargin
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(
      () => new StringReader(source), "WideMismatch.on"))) match {
      case CompilationOutcome.Failure(errors) =>
        val codes = errors.flatMap(_.errorCode).toSet
        assert(codes.contains("E0068"))
        assert(codes.contains("E0037"))
      case success => fail(s"expected both override and abstract-contract errors, got $success")
    }
  }
}
