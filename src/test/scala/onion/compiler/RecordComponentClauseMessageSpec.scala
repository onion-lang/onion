package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * E0061 names the clause that could not produce a component. It used to say
 * "cannot be derived from a `from re"..."` clause" for every record, including one
 * that only has `shape doc = json` and no `from` at all, which sent the reader
 * looking for a clause they never wrote.
 */
class RecordComponentClauseMessageSpec extends AnyFunSpec {
  private val key = "error.semantic.recordFromComponentUnsupported"

  private def failure(src: String): Seq[CompileError] =
    new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
      case _                                  => Seq.empty
    }

  private def e0061(src: String): String = {
    val found = failure(src).filter(_.errorCode.contains("E0061"))
    assert(found.nonEmpty, s"expected an E0061 for:\n$src")
    found.map(_.message).mkString("\n")
  }

  // The clause text is spliced in verbatim, so these substrings hold in both locales.
  it("names the shape clause for a json shape, and does not mention `from`") {
    val msg = e0061(
      """record Summary(title: String, actions: List[String]) {
        |  shape doc = json
        |}
        |println("x")
        |""".stripMargin)
    assert(msg.contains("shape doc = json"), msg)
    assert(!msg.contains("from re"), msg)
  }

  it("names the shape clause for a regex shape") {
    val msg = e0061(
      """record Inner(x: Int)
        |record R(a: String, b: Inner) {
        |  shape line = re"(\S+) (\S+)"
        |}
        |println("x")
        |""".stripMargin)
    assert(msg.contains("shape line = re\"...\""), msg)
  }

  it("still names `from re\"...\"` for a pattern-attached record") {
    val msg = e0061(
      """record Inner(x: Int)
        |record R(a: String, b: Inner) from re"(\S+) (\S+)"
        |println("x")
        |""".stripMargin)
    assert(msg.contains("from re\"...\""), msg)
  }

  it("leaves no unfilled placeholder in either bundle") {
    for (bundle <- Seq(MessageBundles.english, MessageBundles.japanese)) {
      val text = java.text.MessageFormat.format(bundle.getString(key), "b", "Inner", "String, Int", "shape doc = json")
      assert(!text.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $text")
      assert(text.contains("shape doc = json"), text)
    }
  }
}
