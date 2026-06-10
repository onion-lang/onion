package onion.compiler.tools

import java.io.File

import onion.compiler.{CompilerConfig, FileInputSource, OnionCompiler}
import onion.compiler.exceptions.CompilationException
import org.scalatest.funspec.AnyFunSpec

/**
 * Guards the invariant that the compiler never crashes: for any input, compilation
 * either succeeds or reports proper user-facing errors. Two crash modes are caught:
 *   - a Throwable escaping compileDetailed (fatal errors like StackOverflowError
 *     bypass PipelineRunner's NonFatal handler; CompilationException is legitimate)
 *   - an I0000 "Internal compiler error" diagnostic, which is how PipelineRunner
 *     reports a swallowed NonFatal crash (NPE, ClassCastException, MatchError, ...)
 *
 * Inputs come from two places:
 *   - src/test/resources/crash-corpus/  — minimized reproducers of past compiler
 *     crashes (named NNN-description.on so the corpus reads as a history)
 *   - run/  — the shipped example programs, which must always compile cleanly
 */
class CompilerCrashRegressionSpec extends AnyFunSpec {

  private val internalErrorCode = "I0000"

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  /** Returns a crash description if the compiler crashed, None otherwise. */
  private def compileForCrash(file: File): Option[String] =
    try {
      val result = new OnionCompiler(newConfig).compileDetailed(Seq(new FileInputSource(file.getPath)))
      val internalErrors = result.allErrors.filter(_.errorCode.contains(internalErrorCode))
      internalErrors.headOption.map(e => s"internal compiler error: ${e.message}")
    } catch {
      case _: CompilationException => None
      case e: Throwable => Some(s"${e.getClass.getName}: ${e.getMessage}")
    }

  private def assertNoCrash(file: File): Unit =
    compileForCrash(file).foreach { description =>
      fail(s"Compiler crashed on ${file.getPath}: $description")
    }

  private def onFilesIn(dir: File): Seq[File] =
    Option(dir.listFiles())
      .map(_.toSeq.filter(f => f.isFile && f.getName.endsWith(".on")).sortBy(_.getName))
      .getOrElse(Seq.empty)

  describe("crash corpus") {
    val corpusDir = new File("src/test/resources/crash-corpus")
    val corpusFiles = onFilesIn(corpusDir)

    it("contains at least one reproducer") {
      assert(corpusFiles.nonEmpty, s"no .on files found in ${corpusDir.getPath}")
    }

    for (file <- corpusFiles) {
      it(s"does not crash on ${file.getName}") {
        assertNoCrash(file)
      }
    }
  }

  describe("example programs in run/") {
    val exampleFiles = onFilesIn(new File("run"))

    it("finds the example programs") {
      assert(exampleFiles.nonEmpty, "no .on files found in run/")
    }

    for (file <- exampleFiles) {
      it(s"compiles ${file.getName} without crashing") {
        assertNoCrash(file)
      }
    }
  }
}
