package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}

/**
 * A C/Java/C#-style prefix cast, `(Type) expr`, parses as a parenthesized
 * expression followed by a stray second expression -- the raw expected-token
 * dump never mentions Onion's postfix `as` cast. See CLAUDE.md's syntax
 * mistakes table: `(expr as Type)` is correct, `(Type) expr` is not.
 */
class CStyleCastHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new java.io.StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("C-style cast used in place of `as`") {
    it("hints at `as` for a simple type") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object = "hi"
          |    val s: String = (String) o
          |    IO::println(s)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("as"), s"expected a hint mentioning `as`, got: $msgs")
      assert(msgs.contains("expr as String"), s"expected the hint to show the correct form, got: $msgs")
    }

    it("does not fire for the correct `(expr as Type)` form") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object = "hi"
          |    val s: String = (o as String)
          |    IO::println(s)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `as` cast form, got: $msgs")
    }
  }
}
