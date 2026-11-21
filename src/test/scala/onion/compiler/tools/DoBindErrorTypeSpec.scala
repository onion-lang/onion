package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** Two error types in one `do[Result]` block (#2670): the error names the monad values and the
 *  clause that returned the odd one, not the `Function1` the desugaring built. */
class DoBindErrorTypeSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case _ => Seq.empty
    }
  }

  private val src =
    "def p(s: String): Result[Int, String] = Result::ok(1)\n" +
    "def q(s: Int): Result[Int, Int] = Result::err(3)\n" +
    "val r = do[Result] {\n  a <- p(\"x\")\n  b <- q(a)\n  yield a + b\n}\n"

  describe("a do[Result] block with two error types") {
    it("names the Result types, not Function1") {
      val es = errors(src)
      assert(es.size == 1, es.map(_.message).mkString("\n"))
      assert(es.head.errorCode.contains("E0000"))
      assert(!es.head.message.contains("Function1"), es.head.message)
      assert(es.head.message.contains("Result"), es.head.message)
    }

    it("points at the clause that returned the other error type") {
      val es = errors(src)
      assert(es.head.location.line == 5, es.head.location.toString)
    }
  }
}
