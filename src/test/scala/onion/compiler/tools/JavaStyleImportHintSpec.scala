package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion's `import` decl always wraps its targets in braces (`import { java.util.List }`),
 * unlike the Java/C#-style `import java.util.List;` form. `import` is a real keyword, so
 * the parser consumes it and then trips on the identifier that follows, expecting `{` --
 * the generic "block expected" fallback fires instead of anything mentioning braced imports.
 */
class JavaStyleImportHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style import statement") {
    it("hints at the braced form for `import java.util.List;`") {
      val msgs = messages(
        """
          |import java.util.List;
          |
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("import { java.util.List }"), s"expected a hint mentioning the braced form, got: $msgs")
    }

    it("hints for a wildcard import with no trailing semicolon") {
      val msgs = messages(
        """
          |import java.util.*
          |
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("import { java.util.List }"), s"expected a hint mentioning the braced form, got: $msgs")
    }

    it("does not fire for the correct braced import") {
      val msgs = messages(
        """
          |import {
          |  java.util.List
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for a correctly braced import, got: $msgs")
    }
  }
}
