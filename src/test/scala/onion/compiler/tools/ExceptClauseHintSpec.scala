package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no Python-style `except` clause on a `try` block -- exceptions
 * are caught with `catch`. `except` isn't a keyword in Onion, so it parses
 * as a bare identifier-reference statement and the parser actually trips on
 * whatever follows it (the exception type or bound name), never on `except`
 * itself.
 */
class ExceptClauseHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("except clause") {
    it("hints at catch for a Python-style except chained onto the closing brace") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      IO::println("risky")
          |    } except e: Exception {
          |      IO::println("caught")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("catch"), s"expected a hint mentioning catch, got: $msgs")
      assert(msgs.contains("except"), s"expected the hint to name except, got: $msgs")
    }

    it("hints at catch for a Python `except Type as name:` clause") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      IO::println("risky")
          |    } except Exception as e {
          |      IO::println("caught")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("catch"), s"expected a hint mentioning catch, got: $msgs")
      assert(msgs.contains("except"), s"expected the hint to name except, got: $msgs")
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
      assert(!msgs.contains("except"), s"hint should not fire without an `except`, got: $msgs")
    }
  }
}
