package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test: a `do[...]` block's intermediate, non-bound statement (`e; rest`,
 * desugared as `e.bind((_) -> rest)`) reported any error about that synthesized `.bind`
 * call at the location of the *previous* clause instead of the statement's own location
 * -- `desugarDoStatements`'s side-effect-clause case built the `MethodCall` (and its
 * closure/argument nodes) from the recursion's `loc` parameter instead of `expr.location`.
 * A newcomer writing a plain statement (e.g. a non-monadic call) between two `do`-block
 * clauses would see the error pointing one line too high.
 */
class DoNotationSideEffectLocationSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("a do-block statement that is not itself a monadic value (plain void call)") {
    it("reports the bind-target error at the statement's own line, not the previous clause's") {
      val result = compile(
        """
          |record Pair(a: Int, b: Int)
          |
          |def main: void {
          |  val pairs: List[Pair] = [new Pair(1,2)]
          |  val sums = do[List] {
          |    p <- pairs
          |    IO::println("processing")
          |    yield p.a() + p.b()
          |  }
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      // IO::println(...) returns void, so it cannot itself serve as the monadic value the
      // synthesized `.bind` call needs; that failure must be reported at line 8 (the
      // `IO::println(...)` statement), not line 7 (`p <- pairs`, the preceding clause).
      assert(
        errors.exists(e => e.location != null && e.location.line == 8),
        s"expected the error anchored at line 8 (the IO::println statement), got: ${errors.map(e => (e.location, e.message))}"
      )
      assert(
        !errors.exists(e => e.location != null && e.location.line == 7),
        s"the preceding `p <- pairs` clause must not be blamed for the next statement's error, got: ${errors.map(e => (e.location, e.message))}"
      )
    }
  }
}
