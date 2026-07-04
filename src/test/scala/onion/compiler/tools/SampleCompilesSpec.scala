package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.{File, StringReader}
import scala.io.Source

/**
 * Compiles every example program under `run/`, so the canonical samples the
 * README and CLAUDE.md advertise as "verified to work" actually stay compilable
 * as the language evolves. This is a compile-only check (many samples read
 * stdin, files, or the network at run time); a handful with deterministic
 * output are additionally executed in RunSamplesSpec / SampleProgramsSpec.
 */
class SampleCompilesSpec extends AnyFunSpec {

  private def config: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 100)

  private def compileErrors(code: String, name: String): Seq[String] =
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(code), name)))
      .allErrors
      .map(_.message)
      .toSeq

  private def samples: Seq[File] =
    Option(new File("run").listFiles()).getOrElse(Array.empty[File]).toSeq
      .filter(_.getName.endsWith(".on"))
      .sortBy(_.getName)

  describe("run/ sample programs compile") {
    val files = samples

    it("finds the sample programs") {
      assert(files.nonEmpty, "no .on samples found under run/")
    }

    files.foreach { f =>
      it(s"run/${f.getName} compiles") {
        val code = Source.fromFile(f, "UTF-8").mkString
        val errs = compileErrors(code, f.getName)
        assert(errs.isEmpty, s"run/${f.getName} did not compile: ${errs.mkString("; ")}")
      }
    }
  }
}
