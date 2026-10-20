package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Pre-1.0 consolidation: a do block's final value is written `yield e`, not `ret e`.
 * `yield` is now a reserved word (a Java method named `yield` is called with backticks,
 * `` Thread::`yield`() ``), and `ret` is an ordinary identifier again. Writing the old
 * `ret e` in a do block is a syntax error whose hint names the new spelling.
 */
class YieldKeywordSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
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

  describe("yield in a do block") {
    it("gives the final value of an Option comprehension") {
      assert(eval("""val r = do[Option] { a <- Option::of(1); b <- Option::of(2); yield a + b }
                    |return "" + r.getOrElse(0)""".stripMargin) == Shell.Success("3"))
    }
    it("gives the elements of a List comprehension") {
      assert(eval("""val r = do[List] { x <- [1, 2]; y <- ["a", "b"]; yield x + y }
                    |return "" + r""".stripMargin) == Shell.Success("[1a, 1b, 2a, 2b]"))
    }
    it("works on its own line") {
      assert(eval("""val r = do[Option] {
                    |  a <- Option::of(20)
                    |  yield a * 2
                    |}
                    |return "" + r.getOrElse(0)""".stripMargin) == Shell.Success("40"))
    }
  }

  describe("the old ret") {
    it("is a syntax error with a hint naming yield") {
      val msgs = messagesOf("def main(): void {\n  val r = do[Option] { a <- Option::of(1); ret a + 1 }\n  println(r)\n}\n")
      assert(msgs.nonEmpty && msgs.contains("yield"), msgs)
    }
    it("is an ordinary identifier now") {
      assert(eval("""val ret = 5
                    |return "" + (ret + 1)""".stripMargin) == Shell.Success("6"))
    }
  }

  describe("yield as a reserved word") {
    it("calls a Java method named yield with backticks") {
      assert(eval("""Thread::`yield`()
                    |return "ok"""".stripMargin) == Shell.Success("ok"))
    }
    it("is rejected as a plain identifier") {
      assert(messagesOf("val yield = 1\n").nonEmpty)
    }
  }
}
