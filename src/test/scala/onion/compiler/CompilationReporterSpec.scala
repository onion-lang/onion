package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.{ByteArrayOutputStream, PrintStream}

class CompilationReporterSpec extends AnyFunSpec with Diagrams {
  it("formats errors without source file paths") {
    val buffer = new ByteArrayOutputStream()
    val stream = new PrintStream(buffer)
    try {
      CompilationReporter.printErrors(
        Seq(CompileError("", null, "Internal compiler error", Some("I0000"))),
        stream
      )
      stream.flush()
    } finally {
      stream.close()
    }
    val lines = buffer.toString("UTF-8").split("\\R").toSeq
    val errorLine = lines.find(_.contains("Internal compiler error"))
    assert(errorLine.contains("[I0000] Internal compiler error"))
  }
}
