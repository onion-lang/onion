package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion's `for` loop is never parenthesized (`for var i: Int = 0; i < 10; i++ { ... }`),
 * unlike the C/Java/JS `for (init; cond; step) { ... }` form. `for` is a real keyword, so
 * the parser consumes it and then tries to parse the `(` as the start of a parenthesized
 * initializer expression -- it trips several tokens later (on the identifier after the
 * type name, or similar), never mentioning that `for` itself takes no parens.
 */
class CStyleForLoopHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("C-style for loop") {
    it("hints at the unparenthesized form for `for (int i = 0; i < 10; i++)`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    for (int i = 0; i < 10; i++) {
          |      IO::println(i)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("for var i"), s"expected a hint mentioning the unparenthesized form, got: $msgs")
    }

    it("hints for a for loop with only whitespace between `for` and `(`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    for   (var i = 0; i < 10; i = i + 1) {
          |      IO::println(i)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("for var i"), s"expected a hint mentioning the unparenthesized form, got: $msgs")
    }

    it("does not fire for the correct unparenthesized for loop") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    for var i: Int = 0; i < 10; i++ {
          |      IO::println(i)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for a correct for loop, got: $msgs")
    }
  }
}
