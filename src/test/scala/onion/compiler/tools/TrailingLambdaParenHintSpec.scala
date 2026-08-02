package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A trailing lambda's parameter list is never parenthesized (`{ k, v => ... }`,
 * not `{ (k, v) => ... }`) — that's valid only for the non-trailing `(x) -> body`
 * form. Writing parens in the trailing-block form is a common slip since both
 * forms are documented side by side; the parser should name the mistake instead
 * of just dumping the raw expected-token list at the `{`.
 */
class TrailingLambdaParenHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("trailing lambda with parenthesized params") {
    it("hints at the unparenthesized form for a two-arg lambda") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val m: Map[String, Int] = ["a": 1]
          |    val r = m.filter { (k, v) => v > 0 }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("k, v =>"), s"expected a hint naming the unparenthesized form, got: $msgs")
    }

    it("hints at the unparenthesized form for a single-arg lambda") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    val r = xs.map { (x) => x * 2 }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("x =>"), s"expected a hint naming the unparenthesized form, got: $msgs")
    }

    it("does not add the hint for an unrelated unexpected brace") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1 {
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("=>"), s"did not expect the lambda hint here, got: $msgs")
    }
  }
}
