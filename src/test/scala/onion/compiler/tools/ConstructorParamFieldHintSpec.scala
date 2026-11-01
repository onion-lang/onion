package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A plain primary-constructor parameter (no `val`/`var`) is not a field, so a method that reads
 * it is E0002. The error names the fix instead of a similar-looking local (#2515).
 */
class ConstructorParamFieldHintSpec extends AbstractShellSpec {
  private def messages(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("plain primary constructor parameter read in a method") {
    it("says it is a constructor parameter and suggests val") {
      val msgs = messages(
        """class Lru(cap: Int) {
          |public:
          |  def over(n: Int): Boolean = n > cap
          |}
          |""".stripMargin)
      assert(msgs.contains("val cap"), s"expected a `val cap` hint, got: $msgs")
    }

    it("does not fire for a val parameter or an unrelated unknown name") {
      assert(messages(
        """class Lru(val cap: Int) {
          |public:
          |  def over(n: Int): Boolean = n > cap
          |}
          |""".stripMargin) == "")
      val msgs = messages(
        """class Lru(cap: Int) {
          |public:
          |  def over(n: Int): Boolean = n > other
          |}
          |""".stripMargin)
      assert(msgs.contains("other") && !msgs.contains("val other"), msgs)
    }
  }
}
