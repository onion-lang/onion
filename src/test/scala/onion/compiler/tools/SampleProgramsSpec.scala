package onion.compiler.tools

import java.io.File
import scala.io.Source

/**
 * Regression guard for the run/ sample corpus: every .on sample must compile
 * with no errors. This catches a sample silently rotting (e.g. run/Extension.on
 * once broke with an ambiguous-method error that no spec covered). Compile-only,
 * so samples that read stdin or loop at runtime are still checked safely.
 */
class SampleProgramsSpec extends AbstractShellSpec {
  private val sampleDir = new File("run")
  private val samples: Array[File] =
    Option(sampleDir.listFiles((_, name) => name.endsWith(".on")))
      .getOrElse(Array.empty[File])
      .sortBy(_.getName)

  describe("run/ sample programs") {
    it("finds a non-empty sample corpus") {
      assert(samples.nonEmpty, "no .on samples found under run/")
    }

    samples.foreach { f =>
      it(s"compiles ${f.getName}") {
        val src = Source.fromFile(f, "UTF-8")
        val content = try src.mkString finally src.close()
        assert(shell.compiles(content, f.getName), s"${f.getName} failed to compile")
      }
    }
  }
}
