package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style parenthesized `catch (e: Exception) { }` clause. `catch` is a
 * real keyword in Onion, but its binding is never parenthesized, so the parser
 * expects the variable name directly after `catch` and trips on the `(` with an
 * expected-token dump that never mentions the actual mistake.
 */
class CatchParensHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("parenthesized catch clause") {
    it("hints at the unparenthesized form for `catch (e: Exception) { }`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      IO::println("hi")
          |    } catch (e: Exception) {
          |      IO::println("caught")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("catch e: Exception"), s"expected a hint showing the unparenthesized form, got: $msgs")
      assert(msgs.contains("catch (e: Exception)"), s"expected the hint to name the mistaken form, got: $msgs")
    }

    it("does not fire for the correct unparenthesized form") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      IO::println("hi")
          |    } catch e: Exception {
          |      IO::println("caught")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for valid syntax, got: $msgs")
    }
  }
}
