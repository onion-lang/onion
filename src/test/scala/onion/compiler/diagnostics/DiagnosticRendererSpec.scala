package onion.compiler.diagnostics

import onion.compiler.{CompileError, CompileWarning, Location, WarningCategory}
import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.{ByteArrayOutputStream, PrintStream}

class DiagnosticRendererSpec extends AnyFunSpec with Diagrams {
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
