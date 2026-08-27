package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no Python/Ruby-style `not` operator for boolean negation --
 * conditions are negated with `!`. `not` isn't a keyword in Onion, so it
 * parses as a bare identifier-reference expression and the parser actually
 * trips on whatever follows it, never on `not` itself.
 */
class NotOperatorHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("not operator") {
    it("hints at ! for a Python-style `not` in an if condition") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val done = false
          |    if not done {
          |      IO::println("not done")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("!"), s"expected a hint mentioning !, got: $msgs")
      assert(msgs.contains("not"), s"expected the hint to name not, got: $msgs")
    }

    it("hints at ! for a Python-style `not` in a while condition") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val empty = true
          |    while not empty {
          |      IO::println("looping")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("!"), s"expected a hint mentioning !, got: $msgs")
      assert(msgs.contains("not"), s"expected the hint to name not, got: $msgs")
    }

    it("does not fire for an unrelated bare-identifier-statement typo") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    foo bar
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("not"), s"hint should not fire without a `not`, got: $msgs")
    }
  }
}
