package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java/C++-style generic type with angle brackets, `List<String>`, in a type-annotation
 * position (a `val`/`var` declaration, a parameter type, ...). Onion's generics use square
 * brackets (`List[String]`) -- `<` and `>` are only comparison operators, never valid in a
 * type -- so the parser accepts the bare type name and then trips on the `<`, expecting the
 * declaration to end (`=`, `;`, newline, ...), never mentioning `[`.
 */
class JavaStyleGenericsHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style generic angle brackets") {
    it("hints at `[...]` for a `var` declaration's type") {
      val msgs = messages(
        """
          |class Main {
          |  static def main(args: String[]): void {
          |    var xs: List<String> = null
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("List[String]"), s"expected a hint mentioning `List[String]`, got: $msgs")
    }

    it("hints at `[...]` for a `val` declaration's type") {
      val msgs = messages(
        """
          |class Main {
          |  static def main(args: String[]): void {
          |    val b: Box<String> = null
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("Box[String]"), s"expected a hint mentioning `Box[String]`, got: $msgs")
    }

    it("hints at `[...]` for a parameter's type") {
      val msgs = messages(
        """
          |class Main {
          |  static def foo(x: List<String>): void { }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("List[String]"), s"expected a hint mentioning `List[String]`, got: $msgs")
    }

    it("does not fire for the correct `List[String]` form") {
      val msgs = messages(
        """
          |class Main {
          |  static def main(args: String[]): void {
          |    var xs: List[String] = null
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `List[String]` form, got: $msgs")
    }
  }
}
