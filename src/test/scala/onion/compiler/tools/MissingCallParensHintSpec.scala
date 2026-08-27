package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A call whose arguments are written without parentheses (a Python 2
 * `print "x"` or Ruby `puts x` habit) parses as a bare identifier-reference
 * statement followed by a stray token, with a generic expected-token dump
 * that never mentions the missing parentheses.
 */
class MissingCallParensHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("missing call parentheses") {
    it("hints at parentheses for a call written without them") {
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
      assert(msgs.contains("foo(...)"), s"expected a hint mentioning foo(...), got: $msgs")
    }

    it("hints at parentheses for a Python2-style `print` statement") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    println "Hello"
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("println(...)"), s"expected a hint mentioning println(...), got: $msgs")
    }

    it("does not fire when the leading token is a keyword, not a call name") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 5
          |    return x
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("(...)"), s"hint should not fire for a well-formed val, got: $msgs")
    }
  }
}
