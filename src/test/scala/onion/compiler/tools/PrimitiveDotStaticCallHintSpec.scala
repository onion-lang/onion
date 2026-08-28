package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `Int`/`Long`/`Double`/... are reserved keyword tokens in Onion, valid only in a type
 * position or before `::` for static member access (`Long::toString(3L)`) -- they can
 * never start a value expression. So a Java-style `Long.toString(3L)` (dot instead of
 * `::`) doesn't fail on the `.`; the parser trips on `Long` itself, since no expression
 * can start with that token, and the resulting message is a generic expected-token dump
 * that never mentions `::`.
 */
class PrimitiveDotStaticCallHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style dot access on a primitive type name") {
    it("hints at `::` for `Long.toString(3L)`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    IO::println(Long.toString(3L))
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("Long::toString"), s"expected a hint mentioning `Long::toString`, got: $msgs")
    }

    it("hints for other primitive type names too, e.g. `Int.parseInt(...)`") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val n = Int.parseInt("3")
          |    return n
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("Int::parseInt"), s"expected a hint mentioning `Int::parseInt`, got: $msgs")
    }

    it("does not fire for the correct `::` form") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    IO::println(Long::toString(3L))
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `::` form, got: $msgs")
    }
  }
}
