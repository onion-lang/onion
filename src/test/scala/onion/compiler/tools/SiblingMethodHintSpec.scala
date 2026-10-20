package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `Csv::stringify(rows)` with header-mapped rows (what `csvRows()`/`parseWithHeader`
 * return) failed listing only `available: stringify(List[List[String]])`; the method
 * that takes those rows, `stringifyWithHeader`, went unmentioned. When the arguments fit
 * a sibling whose name extends the called one, the hint now names it.
 */
class SiblingMethodHintSpec extends AbstractShellSpec {
  private def messagesOf(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => e.code.getOrElse("") + " " + e.message).mkString("\n")
      case _ => ""
    }
  }

  describe("a sibling method that accepts the arguments") {
    it("Csv::stringify on header-mapped rows names stringifyWithHeader") {
      val msgs = messagesOf(
        """val rows = [["name": "a", "n": "1"]]
          |println(Csv::stringify(rows))
          |""".stripMargin)
      assert(msgs.contains("E0005") && msgs.contains("stringifyWithHeader"), msgs)
    }
    it("still lists the same-name signatures when no sibling fits") {
      val msgs = messagesOf("""println(Csv::stringify(42))""")
      assert(msgs.contains("E0005") && msgs.contains("stringify(List") && !msgs.contains("stringifyWithHeader"), msgs)
    }
  }
}
