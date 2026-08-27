package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A hard keyword token (`class`, `type`, `case`, ...) used where an identifier is
 * expected, e.g. `val class = 5`. Onion lets a reserved word be used as an
 * identifier by escaping it with backticks (`` `class` ``), but the raw
 * expected-token dump (`<ID>, <QUOTED_ID>`) never mentions that. A *soft* keyword
 * like `conforms`/`from`/`shape` lexes as a plain `<ID>` and is already a legal
 * identifier on its own, so it must not trigger this hint.
 */
class ReservedWordIdentifierHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("reserved word used as an identifier") {
    it("hints at backtick-escaping for `val class = 5` at top level") {
      val msgs = messages(
        """
          |val class = 5
          |IO::println(class)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("`class`"), s"expected a hint mentioning backtick-escaped `class`, got: $msgs")
    }

    it("hints at backtick-escaping for a `type` local inside a method body") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val type = 5
          |    IO::println(type)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("`type`"), s"expected a hint mentioning backtick-escaped `type`, got: $msgs")
    }

    it("does not fire for the correct backtick-escaped form") {
      val msgs = messages(
        """
          |val `class` = 5
          |IO::println(`class`)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct backtick-escaped form, got: $msgs")
    }

    it("does not fire for the soft keyword `conforms` used as a plain identifier") {
      val msgs = messages(
        """
          |val conforms = 5
          |IO::println(conforms)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the soft keyword `conforms` used as an identifier, got: $msgs")
    }
  }
}
