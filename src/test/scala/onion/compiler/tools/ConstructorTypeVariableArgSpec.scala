package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/** A constructor argument is checked against an abstract `T` slot (#2332). */
class ConstructorTypeVariableArgSpec extends AbstractShellSpec {
  private def program(arg: String, tparams: String = "[T, U]", field: String = "U") =
    s"""class Box[T] { val v: T
       |public:
       |  def this(x: T) { v = x }
       |  def get(): T = v }
       |class S$tparams {
       |  val u: $field
       |public:
       |  def this(y: $field) { u = y }
       |  def mk(): Box[T] = new Box[T]($arg)
       |}
       |def main(args: String[]): Int { return 0 }
       |""".stripMargin

  private def errorCodes(source: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(source), "Box.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _                                => Seq.empty
    }
  }

  describe("new Box[T](arg) inside a generic class") {
    it("rejects an unrelated type variable U (E0021)") {
      assert(Shell.Failure(-1) == shell.run(program("u"), "None", Array()))
      assert(errorCodes(program("u")) == Seq(Some("E0021")))
    }
    it("still accepts a T") {
      assert(Shell.Success(0) == shell.run(program("u", "[T]", "T"), "None", Array()))
    }
  }
}
