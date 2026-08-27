package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A JS/TS-style `const x = 1` variable declaration. `const` is a reserved
 * keyword token (`K_CONST`) that no production ever references, so it can
 * only reach the parser as this mistake. It trips the parser immediately --
 * at statement position `const` fits no production -- with an expected-token
 * dump that never mentions `val`/`var`.
 */
class JsStyleConstHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("JS-style `const` declaration") {
    it("hints at `val`/`var` for a top-level `const x: Int = 5`") {
      val msgs = messages(
        """
          |const x: Int = 5
          |IO::println(x)
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("val name: Type = value"), s"expected a hint mentioning `val name: Type = value`, got: $msgs")
    }

    it("hints at `val`/`var` for a `const` declaration inside a method body") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    const x: Int = 5
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
  }
}
