package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java/Kotlin-style `this(...)` / `super(...)` statement as the first line of a
 * constructor body. Onion delegates in the header (`def this(s: Int) : this(s, s)`),
 * so the bare syntax error should point there (#2466).
 */
class ConstructorBodyDelegationHintSpec extends AbstractShellSpec {
  private def messages(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("constructor delegation written in the body") {
    it("hints at the header form for `this(...)` on its own line") {
      val msgs = messages(
        """
          |class R(val w: Int, val h: Int) {
          |public:
          |  def this(s: Int) {
          |    this(s, s)
          |  }
          |}
          |""".stripMargin)
      assert(msgs.contains(": this(s, s)"), s"expected the header-delegation hint, got: $msgs")
    }

    it("hints at the header form for a one-line `{ this(s, s) }`") {
      val msgs = messages(
        """
          |class R(val w: Int, val h: Int) {
          |public:
          |  def this(s: Int) { this(s, s) }
          |}
          |""".stripMargin)
      assert(msgs.contains(": this(s, s)"), s"expected the header-delegation hint, got: $msgs")
    }

    it("hints at the `extends` clause for `super(...)`") {
      val msgs = messages(
        """
          |class Base(val v: Int)
          |class D(val w: Int) extends Base(w) {
          |public:
          |  def this() {
          |    super(1)
          |  }
          |}
          |""".stripMargin)
      assert(msgs.contains("extends Base(x)"), s"expected the extends-clause hint, got: $msgs")
    }

    it("does not fire for the header form") {
      val msgs = messages(
        """
          |class R(val w: Int, val h: Int) {
          |public:
          |  def this(s: Int) : this(s, s) { }
          |}
          |""".stripMargin)
      assert(msgs.isEmpty, s"expected no errors, got: $msgs")
    }
  }
}
