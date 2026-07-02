package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Syntax errors carry friendly hints. Reaching EOF while more input is expected
 * (typically a missing `}`/`)`) suggests a missing closer rather than only
 * dumping the raw expected-token list.
 */
class ParseErrorHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("parse error at EOF") {
    it("reports a missing closer without dumping the raw expected-token list") {
      // A class body left unclosed (missing final }).
      val msgs = messages("class C {\npublic:\n  def f(): Int { return 1 }\n").mkString("\n")
      // Mentions the missing bracket (locale-independent), not the keyword dump.
      assert(msgs.contains("}") || msgs.contains(")"))
      assert(!msgs.contains("abstract") && !msgs.contains("more)"))
    }
  }
}
