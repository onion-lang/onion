package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A JS/TS/Swift/Rust/Kotlin-style `let x = 1` variable declaration. `let`
 * is not a keyword at all in Onion -- it lexes as a plain identifier -- so
 * `let x = 1` is read as a bare identifier-reference statement (`let`)
 * immediately followed by another identifier (`x`), and the parser trips on
 * the name one token past the actual mistake.
 */
class JsStyleLetHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("JS-style `let` declaration") {
    it("hints at `val`/`var` for a top-level `let x: Int = 5`") {
      val msgs = messages(
        """
          |let x: Int = 5
          |IO::println(x)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("val name: Type = value"), s"expected a hint mentioning `val name: Type = value`, got: $msgs")
    }

    it("hints at `val`/`var` for a `let` declaration inside a method body") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    let x: Int = 5
          |    IO::println(x)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("val name: Type = value"), s"expected a hint mentioning `val name: Type = value`, got: $msgs")
    }

    it("does not fire for the correct `val` form") {
      val msgs = messages(
        """
          |val x: Int = 5
          |IO::println(x)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `val` form, got: $msgs")
    }

    it("does not fire for a call to a function literally named `let`") {
      val msgs = messages(
        """
          |def let(x: Int): Int = x
          |IO::println(let(5))
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for a call to a function named `let`, got: $msgs")
    }
  }
}
