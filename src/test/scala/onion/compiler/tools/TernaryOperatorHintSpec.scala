package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no `cond ? a : b` ternary operator — `if`/`else` is a statement,
 * not an expression, so the fix is a differently-shaped rewrite rather than a
 * one-token swap. Without a hint, `?` fails with an expected-token dump that
 * never says the operator doesn't exist.
 */
class TernaryOperatorHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("ternary operator") {
    it("hints that Onion has no ?: operator") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 5
          |    val y = (x > 3) ? 1 : 0
          |    return y
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("if"), s"expected a hint mentioning if/else, got: $msgs")
      assert(msgs.contains("?"), s"expected the hint to name the ternary operator, got: $msgs")
    }
  }
}
