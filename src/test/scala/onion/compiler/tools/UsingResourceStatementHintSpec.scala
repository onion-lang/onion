package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A C#/Python-style `using` resource statement, `using r = new Res() { ... }`.
 * Onion has no `using` keyword, so it parses as a bare identifier-reference
 * statement followed by a stray `r`, and the raw expected-token dump never
 * mentions `try`. The diagnostic now recognizes a leading `using name = expr`
 * line and points at the correct `try (val name = expr) { ... }` form.
 */
class UsingResourceStatementHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("C#/Python-style `using` resource statement") {
    it("hints at `try (val ...)` for `using r = new Res() { ... }`") {
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
          |    using r = new Res() {
          |      IO::println("using")
          |    }
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("try (val r = new Res())"), s"expected a hint mentioning `try (val r = new Res())`, got: $msgs")
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
