package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test: a `do[...]` block's final `ret expr` reported a type-mismatch error at the
 * *preceding* clause's location (the last `<-` binding, or the enclosing `do[M] {` line when
 * there is no binding) instead of `ret`'s own line -- `desugarDoStatements` built the
 * synthesized `M::successful(expr)` call (no-binding case) from the caller-supplied `loc`
 * instead of the `RetStatement`'s own location, and built the binding-to-ret closure's
 * `BlockExpression` from `binding.location` instead of the `ret` expression's location.
 */
class DoNotationRetPositionSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("a do-block's final `ret expr` with no preceding binding") {
    it("reports a type mismatch at ret's own line, not the enclosing do[M] { line") {
      val result = compile(
        """
          |import { onion.Future }
          |
          |def main: void {
          |  val f: Future[Int] = do[Future] {
          |    ret 1.5
          |  }
          |  IO::println(f.await())
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(
        errors.exists(e => e.location != null && e.location.line == 6),
        s"expected the error anchored at line 6 (`ret 1.5`), got: ${errors.map(e => (e.location, e.message))}"
      )
      assert(
        !errors.exists(e => e.location != null && e.location.line == 5),
        s"the enclosing `do[Future] {` line must not be blamed for ret's own mismatch, got: ${errors.map(e => (e.location, e.message))}"
      )
    }
  }

  describe("a do-block's final `ret expr` preceded by a binding") {
    it("reports a type mismatch at ret's own line, not the preceding binding's line") {
      val result = compile(
        """
          |import { onion.Future }
          |
          |def fetchA(): Future[List[Int]] = Future::async { [1, 2, 3] }
          |
          |def main: void {
          |  val f: Future[Int] = do[Future] {
          |    xs <- fetchA();
          |    ret xs.sumBy { v -> v }
          |  }
          |  IO::println(f.await())
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(
        errors.exists(e => e.location != null && e.location.line == 9),
        s"expected the error anchored at line 9 (`ret xs.sumBy { v -> v }`), got: ${errors.map(e => (e.location, e.message))}"
      )
      assert(
        !errors.exists(e => e.location != null && e.location.line == 8),
        s"the preceding `xs <- fetchA();` binding must not be blamed for ret's own mismatch, got: ${errors.map(e => (e.location, e.message))}"
      )
    }
  }
}
