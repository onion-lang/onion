package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no Kotlin-style `when` expression -- `select` covers the same ground.
 * `when` IS reserved in Onion, but only as the case-guard keyword (`case p when
 * guard:`); using it to open a block at statement position trips the parser right
 * on the `when` token itself, with an expected-token dump that names whatever
 * would legally close the enclosing block instead, never mentioning `select`.
 */
class WhenStatementHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("when statement") {
    it("hints at select for an unparenthesized when") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    when x {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("select"), s"expected a hint mentioning select, got: $msgs")
      assert(msgs.contains("when"), s"expected the hint to name when, got: $msgs")
    }

    it("hints at select for a parenthesized when") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    when (x) {
          |    case 1: IO::println("one")
          |    else: IO::println("other")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("select"), s"expected a hint mentioning select, got: $msgs")
      assert(msgs.contains("when"), s"expected the hint to name when, got: $msgs")
    }

    it("does not fire for a legitimate case-guard `when`") {
      val msgs = messages(
        """
          |class Sample {
          |public:
          |  def describe(x: Int): String = {
          |    select x {
          |    case n when n > 0: return "positive"
          |    else: return "other"
          |    }
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("select"), s"hint should not fire for a valid case-guard when, got: $msgs")
    }
  }
}
