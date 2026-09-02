package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no Ruby-style `rescue` clause on a `try` block -- exceptions
 * are caught with `catch`. `rescue` isn't a keyword in Onion, so it parses
 * as a bare identifier-reference statement and the parser actually trips on
 * whatever follows it (the exception type or bound name), never on `rescue`
 * itself.
 */
class RescueClauseHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("rescue clause") {
    it("hints at catch for a Ruby-style rescue chained onto the closing brace") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      IO::println("risky")
          |    } rescue e: Exception {
          |      IO::println("caught")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("catch"), s"expected a hint mentioning catch, got: $msgs")
      assert(msgs.contains("rescue"), s"expected the hint to name rescue, got: $msgs")
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
      assert(!msgs.contains("rescue"), s"hint should not fire without a `rescue`, got: $msgs")
    }
  }
}
