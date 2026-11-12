package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** A `String?` passed to a `String` parameter is E0005 that also names nullability (#2722). */
class NullableArgumentHintSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.nullableArgument"

  private def errorsOf(body: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      s"""
         |def shout(x: String): String = x.toUpperCase()
         |def pick(a: String, b: Int): String = a
         |$body
         |""".stripMargin
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.toSeq
      case _ => Seq.empty
    }
  }

  it("has the hint in both bundles") {
    assert(MessageBundles.english.getString(key).contains("!!"))
    assert(MessageBundles.japanese.getString(key) != MessageBundles.english.getString(key))
  }

  it("keeps E0005 and names the nullable argument for a user function") {
    val errs = errorsOf("val s: String? = null\nprintln(shout(s))")
    assert(errs.exists(_.errorCode.contains("E0005")))
    assert(errs.exists(e => e.message.contains("argument 1") || e.message.contains("引数 1")))
  }

  it("names the right position for a stdlib call") {
    val errs = errorsOf("val s: String? = null\nprintln(pick(\"a\", 1))\nprintln(Strings::trim(s))")
    assert(errs.exists(_.errorCode.contains("E0005")))
    assert(errs.exists(e => e.message.contains("argument 1") || e.message.contains("引数 1")))
  }

  it("adds no hint when the types differ for another reason") {
    val errs = errorsOf("println(shout(42))")
    assert(errs.nonEmpty)
    assert(!errs.exists(e => e.message.contains("non-null") && e.message.contains("argument")))
  }
}
