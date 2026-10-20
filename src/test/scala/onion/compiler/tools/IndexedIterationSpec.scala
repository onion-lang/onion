package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Walking a list with each element's index had no helper -- `withIndex`, `mapIndexed`,
 * `zipWithIndex`, `indexed` were all E0005 -- leaving only a C-style `for var i` loop.
 * `xs.withIndex()` now returns an ordered index -> element Map, so the existing
 * `foreach (i, x) in xs.withIndex()` form walks it; `mapIndexed`/`forEachIndexed` take
 * `(i, x)` lambdas; `zipWithIndex`/`indexed` hint at `withIndex`.
 */
class IndexedIterationSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    val xs = ["a", "b", "c"]
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  private def messagesOf(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("indexed iteration") {
    it("foreach (i, x) in xs.withIndex() walks index and element in order") {
      assert(eval("""var out = ""
                    |foreach (i, x) in xs.withIndex() { out = out + i + x.toUpperCase() }
                    |return out""".stripMargin) == Shell.Success("0A1B2C"))
    }
    it("mapIndexed") {
      assert(eval("""return "" + xs.mapIndexed { i, x -> x + i }""") == Shell.Success("[a0, b1, c2]"))
    }
    it("forEachIndexed") {
      assert(eval("""var n = 0
                    |xs.forEachIndexed { i, x -> n += i }
                    |return "" + n""".stripMargin) == Shell.Success("3"))
    }
    it("zipWithIndex and indexed hint at withIndex") {
      assert(messagesOf("val xs = [1]\nprintln(xs.zipWithIndex())\n").contains(": withIndex"))
      assert(messagesOf("val xs = [1]\nprintln(xs.indexed())\n").contains(": withIndex"))
    }
  }
}
