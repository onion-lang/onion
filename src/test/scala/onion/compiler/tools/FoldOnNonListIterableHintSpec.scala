package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `List` has the pipeline method `fold` (`onion.Colls::fold(List, ...)`), but any other
 * `Iterable` (e.g. `Map::values()`, a `Set`) only has `foldl` (`onion.Iterables::foldl`) --
 * `fold`/`foldl` is the one pipeline method where `map`/`filter`'s List-and-Iterable symmetry
 * breaks (#2053). Calling `.fold(...)` on such a receiver fails with a plain METHOD_NOT_FOUND
 * whose name-similarity suggestion picks an unrelated same-receiver method (e.g. `add`)
 * instead of the one rename that actually fixes the call. `SemanticErrorReporter.
 * reportMethodNotFound` now special-cases a failed `fold` call with the same arity `foldl`
 * takes (initial value + binary function) and hints at `foldl` directly.
 */
class FoldOnNonListIterableHintSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private def messagesOf(src: String): String = errors(src).map(_.message).mkString("\n")
  private def codesOf(src: String): Seq[String] = errors(src).flatMap(_.code)

  describe("fold called on a non-List Iterable") {
    it("hints at foldl for a Map's values()") {
      val src =
        """
          |def main(): void {
          |  val m = ["a": 1, "b": 2]
          |  println(m.values().fold(0) { a, x -> a + x })
          |}
          |""".stripMargin
      assert(codesOf(src).contains("E0005"), s"expected E0005 (method not found), got: ${codesOf(src)}")
      val msgs = messagesOf(src)
      assert(msgs.contains("foldl"), s"expected a hint naming foldl, got: $msgs")
    }

    it("still works normally on an actual List, with no hint needed") {
      val src =
        """
          |def main(): void {
          |  println([1, 2, 3].fold(0) { a, x -> a + x })
          |}
          |""".stripMargin
      assert(errors(src).isEmpty, s"expected List::fold to compile cleanly, got: ${messagesOf(src)}")
    }

    it("does not fire for an unrelated method-not-found call") {
      val src =
        """
          |def main(): void {
          |  val m = ["a": 1, "b": 2]
          |  m.values().frobnicate(0)
          |}
          |""".stripMargin
      assert(codesOf(src).contains("E0005"), s"expected E0005 (method not found), got: ${codesOf(src)}")
      val msgs = messagesOf(src)
      assert(!msgs.contains("foldl"), s"hint should not fire for an unrelated name, got: $msgs")
    }
  }
}
