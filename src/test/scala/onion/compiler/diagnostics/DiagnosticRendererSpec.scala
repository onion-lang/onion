package onion.compiler.diagnostics

import onion.compiler.{CompileError, CompileWarning, Location, WarningCategory}
import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DiagnosticRendererSpec extends AnyFunSpec with Diagrams {
  /**
   * Writes `content` to a temp source file, renders a single-token error whose
   * caret column matches the tab-expanded (tabSize=8) column model, and returns
   * (rendered source line, caret line) with the "  N | " / "    | " gutter
   * stripped so the two can be aligned character-for-character.
   */
  private def renderCaret(content: String, line: Int, column: Int, endColumn: Option[Int] = None): (String, String) = {
    val file = Files.createTempFile("diag-caret", ".on")
    try {
      Files.write(file, content.getBytes(StandardCharsets.UTF_8))
      val loc = endColumn match {
        case Some(ec) => new Location(line, column).withSpan(line, ec)
        case None     => new Location(line, column)
      }
      val error = CompileError(file.toString, loc, "boom", Some("E9999"))
      val rendered = DiagnosticRenderer.formatError(error)
      val lines = rendered.split("\\R", -1)
      val sourceLine = lines.find(_.startsWith(s"  $line | ")).getOrElse(fail(s"no source line in:\n$rendered"))
      val caretLine = lines.find(l => l.startsWith("    | ") && (l.contains('^') || l.contains('~')))
        .getOrElse(fail(s"no caret line in:\n$rendered"))
      // gutter "  N | " and "    | " share the same width for a 1-digit line number
      val gutter = sourceLine.indexOf('|') + 2
      (sourceLine.substring(gutter), caretLine.substring(gutter))
    } finally {
      Files.deleteIfExists(file)
    }
  }

  describe("caret alignment against the tab-expanded column model") {
    it("aligns the caret under a token on a line indented with tabs") {
      // Column model (tabSize=8): two leading tabs -> columns 1..16, so
      // "val x: Int = " occupies 17..29 and "undefinedVar" starts at column 30.
      val src = "class Foo {\n\tdef bar(): void {\n\t\tval x: Int = undefinedVar\n\t}\n}\n"
      val (source, caret) = renderCaret(src, line = 3, column = 30)
      assert(source.indexOf("undefinedVar") == caret.indexOf('^'))
    }

    it("aligns the caret on a line indented with spaces") {
      val src = "class Foo {\n  def bar(): void {\n    val x: Int = undefinedVar\n  }\n}\n"
      val (source, caret) = renderCaret(src, line = 3, column = 18)
      assert(source.indexOf("undefinedVar") == caret.indexOf('^'))
    }

    it("aligns the caret on a line with mixed tabs and spaces") {
      // "\t  " -> tab advances to column 8, then two spaces -> columns 9,10;
      // "val x: Int = " occupies 11..23, "undefinedVar" starts at column 24.
      val src = "class Foo {\n\t def bar(): void {\n\t  val x: Int = undefinedVar\n\t }\n}\n"
      val (source, caret) = renderCaret(src, line = 3, column = 24)
      assert(source.indexOf("undefinedVar") == caret.indexOf('^'))
    }

    it("aligns a multi-caret span underline on a tab-indented line") {
      val src = "class Foo {\n\t\tval x: Int = undefinedVar\n}\n"
      // "undefinedVar" spans columns 30..41 with two leading tabs.
      val (source, caret) = renderCaret(src, line = 2, column = 30, endColumn = Some(41))
      val start = caret.indexOf('~')
      assert(source.indexOf("undefinedVar") == start)
      assert(caret.substring(start) == "~" * "undefinedVar".length)
    }
  }

  it("formats errors without source file paths") {
    val formatted = DiagnosticRenderer.formatErrors(
      Seq(CompileError("", null, "Internal compiler error", Some("I0000")))
    )
    assert(formatted.exists(_.contains("[I0000] Internal compiler error")))
  }

  it("prints warnings with codes and counts") {
    val buffer = new ByteArrayOutputStream()
    val out = new PrintStream(buffer)
    try {
      DiagnosticRenderer.printWarnings(
        Seq(CompileWarning("Warn.on", new Location(3, 5), WarningCategory.UnusedVariable, "unused variable 'x'")),
        out
      )
      out.flush()
    } finally {
      out.close()
    }

    val rendered = buffer.toString("UTF-8")
    assert(rendered.contains("[W0001] Warn.on:3:5: warning: unused variable 'x'"))
    assert(rendered.contains("1 warning(s)"))
  }
}
