package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A JS/TS-style `constructor(...) { }` method, named after the JavaScript/TypeScript
 * convention instead of using `def this`. `constructor` is an ordinary identifier in
 * Onion (not a reserved word), so the parser trips right at it, expecting a member
 * declarator (`def`, `val`, `var`, ...) -- the generic dump never mentions `def this`.
 */
class JsStyleConstructorHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("JS/TS-style constructor method") {
    it("hints at `def this` for `constructor(...) { }`") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |  val y: Int
          |public:
          |  constructor(x: Int, y: Int) {
          |    this.x = x
          |    this.y = y
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("def this"), s"expected a hint mentioning `def this`, got: $msgs")
    }

    it("hints at `def this` for a no-arg `constructor() { }`") {
      val msgs = messages(
        """
          |class Point {
          |  val x: Int
          |private:
          |  constructor() {
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

    it("does not fire for a method that merely calls something named `constructor`") {
      val msgs = messages(
        """
          |class Point {
          |public:
          |  def this() {
          |    val constructor = 1
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(!msgs.contains("def this(...) { ... }`, not `constructor"), s"unexpected constructor hint, got: $msgs")
    }
  }
}
