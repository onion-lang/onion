package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A lambda passed to an overloaded extension method is typed against the overload that
 * can take a lambda, whatever the declaration order, as for class methods (#2447).
 */
class ExtensionOverloadLambdaSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runMain(decls: String, body: String): Shell.Result =
    shell.run(
      s"""
         |$decls
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    return $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a lambda argument to an overloaded extension") {
    it("picks the function overload when it is declared first") {
      val result = runMain(
        """extension String {
          |  def g3(f: (Int) -> String): String = "fn:" + f(1)
          |  def g3(s: Int): String = "int:" + s
          |}""".stripMargin,
        """"a".g3((n: Int) -> "n" + n) + "," + "a".g3(5)"""
      )
      assert(Shell.Success("fn:n1,int:5") == result)
    }

    it("picks the function overload when it is declared last") {
      val result = runMain(
        """extension String {
          |  def g3(s: Int): String = "int:" + s
          |  def g3(f: (Int) -> String): String = "fn:" + f(1)
          |}""".stripMargin,
        """"a".g3((n: Int) -> "n" + n) + "," + "a".g3(5)"""
      )
      assert(Shell.Success("fn:n1,int:5") == result)
    }

    it("tells a function slot from a String slot after a shared first parameter") {
      val result = runMain(
        """extension String {
          |  def g4(xs: List[Int], f: (Int) -> String): String = "fn:" + f(xs[0])
          |  def g4(xs: List[Int], s: String): String = "str:" + s
          |}""".stripMargin,
        """"a".g4([7], (n: Int) -> "n" + n) + "," + "a".g4([7], "q")"""
      )
      assert(Shell.Success("fn:n7,str:q") == result)
    }

    it("takes a trailing lambda next to a String overload") {
      val result = runMain(
        """extension String {
          |  def on(f: () -> String): String = "fn:" + f()
          |  def on(s: String): String = "str:" + s
          |}""".stripMargin,
        """"a".on { "lam" } + "," + "a".on("v")"""
      )
      assert(Shell.Success("fn:lam,str:v") == result)
    }

    it("still rejects a lambda where no overload takes one, with one error") {
      val codes = errorCodes(
        """extension String {
          |  def g5(s: Int): String = "int:" + s
          |  def g5(s: String): String = "str:" + s
          |}
          |IO::println("a".g5((n: Int) -> "n" + n))
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0000")))
    }
  }
}
