package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** A `shape x = ...` clause next to a component `x` generates a second `x()` (#2531). */
class ShapeNamedLikeComponentSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("a record shape named like a component") {
    it("is rejected at the declaration, not at a call site") {
      val results = errors(
        """
          |record Pt(x: Int, y: Int) {
          |  shape x = re"(\d+),(\d+)"
          |}
          |""".stripMargin)
      assert(results.nonEmpty)
      assert(!results.exists(_._1.contains("E0005")))
      assert(results.exists(_._2.contains("x")))
    }

    it("still accepts a shape with another name") {
      assert(errors(
        """
          |record Pt(x: Int, y: Int) {
          |  shape pt = re"(\d+),(\d+)"
          |}
          |""".stripMargin).isEmpty)
    }
  }
}
