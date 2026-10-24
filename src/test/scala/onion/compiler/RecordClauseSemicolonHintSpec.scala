package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A `;` between two clauses of a record body is a syntax error. It gets one error with a
 * hint, not a second, contradictory error from the recovery that follows (#2382).
 */
class RecordClauseSemicolonHintSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.semicolon_between_record_clauses"

  private def compileErrors(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  it("has an English and a Japanese message") {
    assert(MessageBundles.english.getString(key).contains("Hint:"))
    val ja = MessageBundles.japanese.getString(key)
    assert(ja.contains("ヒント") && !ja.contains("Hint:"))
  }

  it("gives one error with the hint for `;` between two shape clauses") {
    val msgs = compileErrors(
      "record Summary(title: String) { shape doc = json; shape txt = re\"(\\w+)\" }\n"
    )
    assert(msgs.size == 1, s"expected one error, got: $msgs")
    assert(msgs.head.contains("`;`") && msgs.head.contains("`shape`"), msgs.head)
  }

  it("gives one error with the hint for `;` between two example clauses") {
    val msgs = compileErrors("record R(a: String) { example e1 { true }; example e2 { true } }\n")
    assert(msgs.size == 1, s"expected one error, got: $msgs")
    assert(msgs.head.contains("`shape`"), msgs.head)
  }

  it("still reports a later declaration's own error after the record") {
    val msgs = compileErrors(
      "record R(a: String) { shape doc = json; shape txt = re\"(\\w+)\" }\nval x: Int = \"s\"\n"
    )
    assert(msgs.exists(_.contains("`shape`")), msgs.toString)
  }

  it("still accepts clauses separated by a newline or a space") {
    assert(compileErrors("record R(a: String) { shape doc = json  shape txt = re\"(\\w+)\" }\n").isEmpty)
  }
}
