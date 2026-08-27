package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style field or local-variable declaration -- `String name;` or
 * `String name = "x";` -- that puts the type before the name instead of using
 * `val`/`var`. As a class member the parser trips on the type identifier itself,
 * expecting a member declarator; as a local statement the type is read as a bare
 * expression and the parser trips on the name that follows. Neither mentions
 * `val`/`var`.
 */
class JavaStyleFieldHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style field/local declaration") {
    it("hints at `val`/`var` for a field declared `String name;`") {
      val msgs = messages(
        """
          |class Point {
          |  String name;
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("var name: String"), s"expected a hint mentioning `var name: String`, got: $msgs")
    }

    it("hints at `val`/`var` for a local declared `String x = \"a\";`") {
      val msgs = messages(
        """
          |def main: void {
          |  String x = "a"
          |  IO::println(x)
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("var x: String"), s"expected a hint mentioning `var x: String`, got: $msgs")
    }

    it("hints at `val`/`var` for a bracketed generic type, `List[String] items;`") {
      val msgs = messages(
        """
          |class Box {
          |  List[String] items;
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("var items: List[String]"), s"expected a hint mentioning `var items: List[String]`, got: $msgs")
    }

    it("does not fire for the correct `var name: String` field form") {
      val msgs = messages(
        """
          |class Point {
          |  var name: String
          |public:
          |  def this(n: String) { name = n }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `var name: String` form, got: $msgs")
    }

    it("does not fire for an ordinary constructor call statement") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |public:
          |  def this(x: Int) { self.x = x }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): void {
          |    val p: Point = new Point(3)
          |    IO::println(p)
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for an ordinary `new Point(3)` call, got: $msgs")
    }
  }
}
