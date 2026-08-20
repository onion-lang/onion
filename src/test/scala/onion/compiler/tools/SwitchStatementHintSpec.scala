package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no `switch` statement -- `select` covers the same ground. `switch`
 * isn't a keyword in Onion, so it parses as a bare identifier reference and the
 * parser actually trips on whatever follows it (the condition, or the `{` of a
 * parenthesized condition), never mentioning `select`.
 */
class SwitchStatementHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("switch statement") {
    it("hints at select for an unparenthesized switch") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    switch x {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("select"), s"expected a hint mentioning select, got: $msgs")
      assert(msgs.contains("switch"), s"expected the hint to name switch, got: $msgs")
    }

    it("hints at select for a parenthesized switch") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    switch (x) {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("select"), s"expected a hint mentioning select, got: $msgs")
      assert(msgs.contains("switch"), s"expected the hint to name switch, got: $msgs")
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
      assert(!msgs.contains("select"), s"hint should not fire without a leading `switch`, got: $msgs")
    }
  }
}
