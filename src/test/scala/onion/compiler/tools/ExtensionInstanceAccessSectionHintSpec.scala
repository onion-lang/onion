package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `extension`/`instance` bodies are a flat list of `def` members with no
 * `public:`/`private:`/`protected:` sections (unlike a class/record/enum body, which
 * has an `access_section()` alternative the parser falls back to instead). Writing
 * one anyway used to fail with the bare "Encountered \"private\", but expecting
 * \"def\"" message and no hint at all.
 */
class ExtensionInstanceAccessSectionHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("access sections inside `extension`/`instance` bodies") {
    it("hints at the no-access-section rule for `private:` in an extension block") {
      val msgs = messages(
        """
          |extension String {
          |private:
          |  def helper(): String = self + "-helper"
          |public:
          |  def greet(): String = self.helper() + "!"
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("extension"), s"expected a hint mentioning `extension`, got: $msgs")
    }

    it("hints at the no-access-section rule for `public:` in an instance block") {
      val msgs = messages(
        """
          |interface Greeter {
          |  def greet(): String
          |}
          |instance Greeter {
          |public:
          |  def greet(): String = "hi"
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("instance"), s"expected a hint mentioning `instance`, got: $msgs")
    }

    it("does not fire for a correctly flat extension block") {
      val msgs = messages(
        """
          |extension String {
          |  def shout(): String = self.toUpperCase() + "!"
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for a correctly flat extension block, got: $msgs")
    }
  }
}
