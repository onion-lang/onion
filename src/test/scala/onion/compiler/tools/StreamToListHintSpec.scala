package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `s.lines()` and `s.chars()` resolve to Java's `String.lines()`/`chars()`, which return a
 * Stream, not a List, so `s.lines().size`, `.first()`, `.join(",")` or `[0]` failed with a
 * misleading guess (`min`, `limit`, `map`). A List-style name on a Stream now says to
 * convert first: `toList()` for a Stream, `boxed().toList()` for an IntStream.
 */
class StreamToListHintSpec extends AbstractShellSpec {
  private def messagesOf(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("a List-style name on a Java Stream") {
    Seq("size", "size()", "first()", "join(\",\")").foreach { access =>
      it(s"s.lines().$access says toList()") {
        val msgs = messagesOf(s"""val s = "a"\nprintln(s.lines().$access)\n""")
        assert(msgs.contains("toList()"), msgs)
      }
    }
    it("indexing a Stream says toList()") {
      assert(messagesOf("val s = \"a\"\nprintln(s.lines()[0])\n").contains("toList()"))
    }
    it("an IntStream (chars()) says boxed().toList()") {
      assert(messagesOf("val s = \"a\"\nprintln(s.chars().size())\n").contains("boxed().toList()"))
    }
    it("leaves a real Stream method alone") {
      assert(messagesOf("val s = \"a\"\nprintln(s.lines().count())\n").isEmpty)
    }
  }
}
