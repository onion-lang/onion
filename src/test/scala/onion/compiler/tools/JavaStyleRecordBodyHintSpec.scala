package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style record body, `record Point { int x; int y; }`, that puts the
 * components inside a brace body instead of Onion's parenthesized component
 * list. The parser accepts `record Name` and then requires either `[` (type
 * parameters) or `(` (the component list) -- it trips on the `{` with an
 * expected-token dump that never mentions the parenthesized form.
 */
class JavaStyleRecordBodyHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style record body") {
    it("hints at the parenthesized component list for `record Point { ... }`") {
      val msgs = messages(
        """
          |record Point {
          |  int x
          |  int y
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("record Point("), s"expected a hint mentioning `record Point(`, got: $msgs")
    }

    it("hints for a generic record, `record Box[T] { ... }`") {
      val msgs = messages(
        """
          |record Box[T] {
          |  T value
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("record Box("), s"expected a hint mentioning `record Box(`, got: $msgs")
    }

    it("does not fire for the correct `record Point(x: Int, y: Int)` form") {
      val msgs = messages(
        """
          |record Point(x: Int, y: Int)
          |class Main {
          |public:
          |  static def main(args: String[]): void {
          |    val p: Point = new Point(1, 2)
          |    IO::println(p.x())
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `record Point(x: Int, y: Int)` form, got: $msgs")
    }

    it("does not fire for a record with a correct parenthesized list followed by a method body") {
      val msgs = messages(
        """
          |record Point(x: Int, y: Int) {
          |public:
          |  def sum: Int = x + y
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for a record with a correct trailing body, got: $msgs")
    }
  }
}
