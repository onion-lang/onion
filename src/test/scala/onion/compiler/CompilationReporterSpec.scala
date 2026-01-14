package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.{ByteArrayOutputStream, PrintStream}

class CompilationReporterSpec extends AnyFunSpec with Diagrams {
  it("formats errors without source file paths") {
    val buffer = new ByteArrayOutputStream()
    val stream = new PrintStream(buffer)
    val originalErr = System.err
    System.setErr(stream)
    try {
      CompilationReporter.printErrors(
        Seq(CompileError("", null, "Internal compiler error", Some("I0000")))
      )
    } finally {
      System.setErr(originalErr)
      stream.close()
    }
    val lines = buffer.toString("UTF-8").split("\\R").toSeq
    assert(lines.nonEmpty)
    assert(lines.head == "[I0000] Internal compiler error")
  }
}
