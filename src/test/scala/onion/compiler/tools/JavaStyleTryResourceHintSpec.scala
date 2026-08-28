package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style try-with-resources declaration, `try (Res r = new Res()) { ... }`,
 * that puts the type before the name instead of using `val`/`var`. Onion's
 * resource clause is `"(" resource_list() ")"` where each resource starts with
 * `val`/`var`, and that whole clause is only attempted with a 2-token lookahead
 * (`"(" ("val"|"var")`) -- when the second token is a type name instead, the
 * lookahead fails, the optional resource clause is skipped entirely, and the
 * parser trips on the `(` itself, expecting the `{` of a resourceless `try`
 * block. The expected-token dump never mentions `val`/`var`.
 */
class JavaStyleTryResourceHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style try-with-resources declaration") {
    it("hints at `val`/`var` for `try (Res r = ...) { ... }`") {
      val msgs = messages(
        """
          |class Res {
          |public:
          |  def this { }
          |  def close: void { }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): void {
          |    try (Res r = new Res()) {
          |      IO::println("using")
          |    }
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("val r: Res"), s"expected a hint mentioning `val r: Res`, got: $msgs")
    }

    it("does not fire for the correct `try (val r = ...) { ... }` form") {
      val msgs = messages(
        """
          |import { java.lang.AutoCloseable }
          |class Res conforms AutoCloseable {
          |public:
          |  def this { }
          |  def close: void { }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): void {
          |    try (val r = new Res()) {
          |      IO::println("using")
          |    }
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `try (val r = ...)` form, got: $msgs")
    }
  }
}
