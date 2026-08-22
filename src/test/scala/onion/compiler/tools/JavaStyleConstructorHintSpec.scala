package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style constructor declaration -- `public ClassName(...) { }`, named after the
 * class instead of using `def this`. `public`/`private`/`protected` are real keywords
 * (access-section markers), so the parser consumes the modifier and then trips on the
 * class name, expecting `:` -- never mentioning `def this`. Without a modifier the bare
 * `ClassName(...) { }` trips at the class-name identifier itself, expecting a member
 * declarator (`def`, `val`, `var`, ...).
 */
class JavaStyleConstructorHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style constructor declaration") {
    it("hints at `def this` for `public ClassName(...) { }`") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |  val y: Int
          |public:
          |  public Point(x: Int, y: Int) {
          |    this.x = x
          |    this.y = y
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def this"), s"expected a hint mentioning `def this`, got: $msgs")
    }

    it("hints at `def this` for a no-arg `private ClassName() { }`") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |private:
          |  private Point() {
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def this"), s"expected a hint mentioning `def this`, got: $msgs")
    }

    it("hints at `def this` for a bare `ClassName(...) { }` with no modifier") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |  val y: Int
          |public:
          |  Point(x: Int, y: Int) {
          |    this.x = x
          |    this.y = y
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def this"), s"expected a hint mentioning `def this`, got: $msgs")
    }

    it("does not fire for the correct `def this` form") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |  val y: Int
          |public:
          |  def this(x: Int, y: Int) {
          |    this.x = x
          |    this.y = y
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `def this` form, got: $msgs")
    }
  }
}
