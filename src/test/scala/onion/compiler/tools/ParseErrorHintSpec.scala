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

  describe("a contract clause written as a record or enum member (#2605)") {
    // A record or enum body lists only `}` as the expected token where a member could start,
    // so the class-body hint never fired and the user saw a bare `expecting "}"`.
    val bodies = Seq(
      "record Pos(n: Int) {\n  requires n >= 0\n}\n",
      "record Pos(n: Int) {\n  invariant n >= 0\n  ensures n >= 0\n}\n",
      "enum E { A, B\n  requires 1 > 0\n}\n",
      "enum S { case A; case B\n  ensures 1 > 0\n}\n"
    )
    bodies.foreach { src =>
      it(s"points to the method header and to `invariant`: ${src.replace("\n", " ").trim}") {
        val msgs = messages(src).mkString("\n")
        // Code fragments only: they are the same in the en and ja messages.
        assert(msgs.contains("public: def half(): Int"), msgs)
        assert(msgs.contains("invariant n >= 0"), msgs)
      }
    }
  }
}
