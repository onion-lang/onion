package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no Rust/Scala/OCaml-style `match` expression -- `select` covers the
 * same ground. `match` isn't a keyword in Onion, so it parses as a bare
 * identifier reference and the parser actually trips on whatever follows it
 * (the scrutinee, or the `{` of a parenthesized scrutinee), never mentioning
 * `select`.
 */
class MatchStatementHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("match statement") {
    it("hints at select for an unparenthesized match") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    match x {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("select"), s"expected a hint mentioning select, got: $msgs")
      assert(msgs.contains("match"), s"expected the hint to name match, got: $msgs")
    }

    it("hints at select for a parenthesized match") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    match (x) {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("select"), s"expected a hint mentioning select, got: $msgs")
      assert(msgs.contains("match"), s"expected the hint to name match, got: $msgs")
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
      assert(!msgs.contains("select"), s"hint should not fire without a leading `match`, got: $msgs")
    }
  }
}
