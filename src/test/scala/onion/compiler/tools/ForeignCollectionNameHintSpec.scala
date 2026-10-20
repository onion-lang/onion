package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A newcomer reaches for the collection method name their last language used: Scala's
 * `sortBy`/`foldLeft`/`forall`, Kotlin's `joinToString`/`sumOf`, Ruby's `each`/`inject`.
 * On a `List` these used to fail with a plain E0005 whose name-similarity guess was
 * either missing or wrong (`sortBy` -> `sort`, `forall` -> `addAll`, `each` -> `add`),
 * even though Onion has the same operation under another name. The hint now names it.
 */
class ForeignCollectionNameHintSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private def messagesOf(src: String): String = errors(src).map(_.message).mkString("\n")
  private def codesOf(src: String): Seq[String] = errors(src).flatMap(_.code)

  private def program(call: String): String =
    s"""
       |def main(): void {
       |  val xs: List[Int] = [3, 1, 2]
       |  println(xs.$call)
       |}
       |""".stripMargin

  private val cases = Seq(
    "sortBy { x -> x }" -> "sortedBy",
    "sortByDescending { x -> x }" -> "sortedByDescending",
    "forall { x -> x > 0 }" -> "all",
    "joinToString(\", \")" -> "join",
    "foldLeft(0) { a, x -> a + x }" -> "fold",
    "inject(0) { a, x -> a + x }" -> "fold",
    "sumOf { x -> x }" -> "sumBy",
    "unique()" -> "distinct",
    "nonEmpty()" -> "isNotEmpty",
    "each { x -> println(x) }" -> "forEach",
    "collect { x -> x * 2 }" -> "map"
  )

  describe("a foreign collection method name on a List") {
    cases.foreach { case (call, onionName) =>
      it(s"hints at $onionName for xs.$call") {
        val src = program(call)
        assert(codesOf(src).contains("E0005"), s"expected E0005, got: ${codesOf(src)}")
        val msgs = messagesOf(src)
        assert(msgs.contains(": " + onionName + "\n") || msgs.endsWith(": " + onionName),
          s"expected a hint naming $onionName, got: $msgs")
      }
    }

    it("every suggested name really compiles on the same List") {
      val fixed = Seq(
        "sortedBy { x -> x }", "sortedByDescending { x -> x }", "all { x -> x > 0 }",
        "join(\", \")", "fold(0) { a, x -> a + x }", "sumBy { x -> x }", "distinct()",
        "isNotEmpty()", "map { x -> x * 2 }"
      )
      fixed.foreach { call =>
        val src = program(call)
        assert(errors(src).isEmpty, s"expected xs.$call to compile, got: ${messagesOf(src)}")
      }
    }

    it("does not fire on a receiver that is not a List") {
      val src =
        """
          |class Box {
          |public:
          |  def this {}
          |}
          |def main(): void {
          |  new Box().sortBy(1)
          |}
          |""".stripMargin
      assert(codesOf(src).contains("E0005"), s"expected E0005, got: ${codesOf(src)}")
      assert(!messagesOf(src).contains("sortedBy"), s"hint should not fire off a List, got: ${messagesOf(src)}")
    }
  }
}
