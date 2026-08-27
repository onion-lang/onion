package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A TypeScript/Kotlin-style union nullable type, `String | null`. Onion writes
 * a nullable type with a trailing `?` (`String?`) -- `|` never appears in a
 * type position, so the parser accepts the base type and then trips right on
 * the `|`, with an expected-token dump that never mentions `?`.
 */
class NullableUnionTypeHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("TypeScript-style `T | null` nullable type") {
    it("hints at `?` for a `val` declaration's type") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: String | null = "a"
          |    IO::println(x)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("String?"), s"expected a hint mentioning `String?`, got: $msgs")
    }

    it("hints at `?` for a parameter's type") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def f(x: String | null): Int { return 0 }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("String?"), s"expected a hint mentioning `String?`, got: $msgs")
    }

    it("does not fire for the correct `String?` nullable form") {
      val msgs = messages(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: String? = "a"
          |    IO::println(x)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `String?` form, got: $msgs")
    }
  }
}
