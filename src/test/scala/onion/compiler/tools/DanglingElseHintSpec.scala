package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A dangling `else` (one with no matching `if` immediately before it) fails
 * parsing at the enclosing block's closing `}`. The hint attached to that
 * error must not claim `if`/`else` cannot be used as an expression — Onion
 * fully supports that (`val x = if cond { a } else { b }`), so the fix is to
 * add the missing `if`, not to rewrite anything as an expression.
 */
class DanglingElseHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("dangling else") {
    it("does not claim if/else is unsupported as an expression") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    else {
          |      IO::println("b")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("does not support"), s"hint wrongly claims if/else-as-expression is unsupported, got: $msgs")
      assert(msgs.contains("if") && msgs.contains("else"), s"expected a hint mentioning if/else, got: $msgs")
    }
  }
}
