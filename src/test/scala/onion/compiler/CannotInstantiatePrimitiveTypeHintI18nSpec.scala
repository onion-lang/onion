package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `new Double(1.5)`, `new Int(3)`, and similar -- a capitalized primitive type name
 * used with `new` -- used to fall through to the generic INCOMPATIBLE_TYPE (E0000),
 * reporting "type Object is expected, but type Double is used" with nothing connecting
 * it back to the actual mistake (CLAUDE.md's own documented anti-pattern: primitives
 * need no construction, and `JDouble`/`JInteger`/... are the boxed wrapper classes).
 * `ConstructionTyping.typeNewObject` now reports the dedicated E0092
 * CANNOT_INSTANTIATE_PRIMITIVE_TYPE instead, with a bilingual message pointing at the
 * fix, like every other hint covered by a `*HintI18nSpec`.
 */
class CannotInstantiatePrimitiveTypeHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.semantic.cannotInstantiatePrimitiveType"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private def compile(src: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
      case _ => Seq.empty
    }
  }

  it("resolves in the English bundle") {
    assert(en.getString(key).nonEmpty)
  }

  it("resolves in the Japanese bundle with actual Japanese text, distinct from English") {
    val text = ja.getString(key)
    assert(!text.toLowerCase(java.util.Locale.ROOT).contains("primitive"), s"leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("fires E0092 for `new Double(...)`, naming the boxed wrapper `JDouble`") {
    val errors = compile(
      """|class Main {
         |public:
         |  static def main(args: String[]): Int {
         |    val d = new Double(1.5)
         |    return 0
         |  }
         |}
         |""".stripMargin)
    assert(errors.exists(_.errorCode.contains("E0092")), s"expected E0092, got: ${errors.map(_.errorCode)}")
    // `JDouble` is a literal identifier, identical in both bundles, so this assertion
    // holds regardless of the JVM's default locale.
    val msgs = errors.map(_.message).mkString("\n")
    assert(msgs.contains("JDouble"), s"expected the boxed-wrapper hint, got: $msgs")
  }

  it("fires E0092 for `new Boolean(...)` too, not just `new Double(...)`") {
    val errors = compile(
      """|class Main {
         |public:
         |  static def main(args: String[]): Int {
         |    val b = new Boolean(true)
         |    return 0
         |  }
         |}
         |""".stripMargin)
    assert(errors.exists(_.errorCode.contains("E0092")), s"expected E0092, got: ${errors.map(_.errorCode)}")
  }

  it("does not fire for a real class construction (`new Foo()`)") {
    val errors = compile(
      """|class Foo { public: def this {} }
         |class Main {
         |public:
         |  static def main(args: String[]): Int {
         |    val f = new Foo()
         |    return 0
         |  }
         |}
         |""".stripMargin)
    assert(!errors.exists(_.errorCode.contains("E0092")), s"unexpected E0092: ${errors.map(_.errorCode)}")
  }

  it("does not fire for `new Int[3]` (array creation, not object construction)") {
    val errors = compile(
      """|class Main {
         |public:
         |  static def main(args: String[]): Int {
         |    val a = new Int[3]
         |    return a[0]
         |  }
         |}
         |""".stripMargin)
    assert(!errors.exists(_.errorCode.contains("E0092")), s"unexpected E0092: ${errors.map(_.errorCode)}")
  }

  it("does not fire for `new JDouble(1.5)` (the actual boxed wrapper class)") {
    val errors = compile(
      """|class Main {
         |public:
         |  static def main(args: String[]): Int {
         |    val d = new JDouble(1.5)
         |    return 0
         |  }
         |}
         |""".stripMargin)
    assert(!errors.exists(_.errorCode.contains("E0092")), s"unexpected E0092: ${errors.map(_.errorCode)}")
  }
}
