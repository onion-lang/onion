package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no Ruby-style `elsif` clause -- `else if` covers the same
 * ground. `elsif` isn't a keyword in Onion, so it parses as a bare
 * identifier-reference statement and the parser actually trips on whatever
 * follows it (the condition), never on `elsif` itself.
 */
class ElsifStatementHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("elsif clause") {
    it("hints at else if for a Ruby-style elsif chained onto the closing brace") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    if x == 1 {
          |      IO::println("one")
          |    } elsif x == 2 {
          |      IO::println("two")
          |    } else {
          |      IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("else if"), s"expected a hint mentioning else if, got: $msgs")
      assert(msgs.contains("elsif"), s"expected the hint to name elsif, got: $msgs")
    }

    it("hints at else if for a leading elsif on its own line") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    if x == 1 {
          |      IO::println("one")
          |    }
          |    elsif x == 2 {
          |      IO::println("two")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("else if"), s"expected a hint mentioning else if, got: $msgs")
      assert(msgs.contains("elsif"), s"expected the hint to name elsif, got: $msgs")
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
      assert(!msgs.contains("else if"), s"hint should not fire without an `elsif`, got: $msgs")
    }
  }
}
