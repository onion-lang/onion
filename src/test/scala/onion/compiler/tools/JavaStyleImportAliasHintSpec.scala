package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java/C#-style import alias written before the target, `Foo = java.lang.Long`,
 * instead of Onion's `java.lang.Long as Foo`. Import entries parse as a bare
 * qualified name with an optional trailing `as alias`, so the parser reads the
 * leading identifier as the start of that qualified name and trips on the `=`,
 * expecting `.` to continue it -- never mentioning `as`.
 */
class JavaStyleImportAliasHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style import alias") {
    it("hints at `as` for `Foo = java.lang.Long;` inside an import block") {
      val msgs = messages(
        """
          |import {
          |  Foo = java.lang.Long;
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
      assert(msgs.contains("java.lang.Long as Foo"), s"expected a hint mentioning `java.lang.Long as Foo`, got: $msgs")
    }

    it("hints at `as` with no trailing semicolon") {
      val msgs = messages(
        """
          |import {
          |  JLong = java.lang.Long
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
      assert(msgs.contains("java.lang.Long as JLong"), s"expected a hint mentioning `java.lang.Long as JLong`, got: $msgs")
    }

    it("does not fire for the correct `as` alias form") {
      val msgs = messages(
        """
          |import {
          |  java.lang.Long as JLong;
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
      assert(msgs.isEmpty, s"expected no errors for the correct `as` alias form, got: $msgs")
    }

    it("does not fire for an ordinary assignment statement") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 0
          |    x = 5
          |    return x
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for an ordinary assignment statement, got: $msgs")
    }

    it("does not fire for an unrelated `module foo = bar` mistake") {
      // A malformed `module` declaration's expected-token set also lists `.` (among
      // `;`, a newline, ...), since `module` parses a dotted qualified name too -- but
      // as one option among several, never as the sole expected token the way an
      // import entry's does. This guards against matching on that broader set.
      val msgs = messages(
        """
          |module foo = bar
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains(" as "), s"expected no import-alias hint for an unrelated `module` mistake, got: $msgs")
    }
  }
}
