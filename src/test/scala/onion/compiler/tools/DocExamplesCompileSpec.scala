package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.{File, StringReader}
import scala.io.Source

/**
 * Compiles every complete example in `docs/examples/` (and its `docs/ja/examples/`
 * mirror) that is labeled with a `**Xxx.on**` filename — the flagship programs a
 * reader copies and runs. Documentation rot in one of those examples (an undefined
 * type, a missing helper, a renamed API) then fails the build instead of silently
 * shipping a doc that does not compile.
 *
 * Only filename-labeled examples are checked: illustrative fragments (syntax
 * snippets that reference placeholders, or blocks marked with `...`) are not
 * complete programs and are intentionally skipped.
 */
class DocExamplesCompileSpec extends AnyFunSpec {

  private def config: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 100)

  private def compileErrors(code: String): Seq[String] =
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(code), "DocExample.on")))
      .allErrors
      .map(_.message)
      .toSeq

  // A **Name.on** label followed by its section body (up to the next heading).
  private val labeled = """(?s)\*\*`([A-Za-z0-9_]+\.on)`\*\*(.*?)(?=\n#{1,3} |\z)""".r
  private val onionBlock = """(?s)```onion\n(.*?)```""".r

  private def dirs = Seq(new File("docs/examples"), new File("docs/ja/examples"))

  private def labeledExamples: Seq[(String, String, String)] =
    dirs.flatMap(d => Option(d.listFiles()).getOrElse(Array.empty[File]).toSeq)
      .filter(_.getName.endsWith(".md"))
      .sortBy(_.getPath)
      .flatMap { f =>
        val txt = Source.fromFile(f, "UTF-8").mkString
        labeled.findAllMatchIn(txt).flatMap { m =>
          val name = m.group(1)
          val blocks = onionBlock.findAllMatchIn(m.group(2)).map(_.group(1))
            .filterNot(_.contains("...")).toSeq
          if (blocks.isEmpty) None
          else Some((f.getPath.replace('\\', '/'), name, blocks.mkString("\n")))
        }
      }

  describe("docs/examples labeled complete programs compile") {
    val examples = labeledExamples

    it("finds the labeled examples") {
      assert(examples.nonEmpty, "no **Xxx.on** labeled examples found under docs/examples")
    }

    examples.foreach { case (path, name, code) =>
      it(s"$path / $name compiles") {
        val errs = compileErrors(code)
        assert(errs.isEmpty, s"$name in $path did not compile: ${errs.mkString("; ")}")
      }
    }
  }
}
