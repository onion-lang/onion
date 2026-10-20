package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * The String counterpart of ForeignCollectionNameHintSpec: Kotlin's `reversed`/`padStart`,
 * Python's `splitlines`/`title`/`find`, JavaScript's `includes`/`trimStart` and the like
 * failed on a String with no hint or a wrong one (`padEnd` -> `indent`, `len` -> `lines`,
 * `find` -> `lines`). Each now names the Onion spelling of the same operation.
 */
class ForeignStringNameHintSpec extends AbstractShellSpec {
  private def messagesOf(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  private val cases = Seq(
    "reversed()" -> "reverse",
    "format(3)" -> "formatted",
    "padStart(5, '*')" -> "padLeft",
    "padEnd(5, '*')" -> "padRight",
    "toUpper()" -> "upper",
    "splitWhitespace()" -> "words",
    "trimStart()" -> "stripLeading",
    "trimEnd()" -> "stripTrailing",
    "splitlines()" -> "lines",
    "len()" -> "length",
    "find(\"b\")" -> "indexOf",
    "includes(\"b\")" -> "contains",
    "title()" -> "capitalizeWords"
  )

  describe("a foreign String method name") {
    cases.foreach { case (call, onionName) =>
      it(s"hints at $onionName for s.$call") {
        val msgs = messagesOf(s"""val s = "abc"\nprintln(s.$call)\n""")
        assert(msgs.contains(": " + onionName + "\n") || msgs.endsWith(": " + onionName), s"expected $onionName, got: $msgs")
      }
    }

    it("every suggested name compiles on a String") {
      Seq("reverse()", "formatted(3)", "padLeft(5, '*')", "padRight(5, '*')", "upper()", "words()",
        "stripLeading()", "stripTrailing()", "lines()", "length()", "indexOf(\"b\")", "contains(\"b\")",
        "capitalizeWords()").foreach { call =>
        val msgs = messagesOf(s"""val s = "abc"\nprintln(s.$call)\n""")
        assert(msgs.isEmpty, s"expected s.$call to compile, got: $msgs")
      }
    }

    it("does not fire the List table on a String, or the String table on a List") {
      assert(!messagesOf("val s = \"abc\"\nprintln(s.forall { c -> true })\n").contains(": all"))
      assert(!messagesOf("val xs = [1]\nprintln(xs.includes(1))\n").contains(": contains"))
    }
  }
}
