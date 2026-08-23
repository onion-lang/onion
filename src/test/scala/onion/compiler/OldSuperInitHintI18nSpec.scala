package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The removed anonymous super-call constructor form, `def this(x) : (x) { }`.
 * After the colon the parser now expects only `this` (delegating with
 * `: this(...)`), so tripping on a `(` there is a strong signal of the old
 * form -- `commonSyntaxHint`'s `case "(" if expected.contains("\"this\"") ...`
 * -- like every other hint in that match, the message must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales; see
 * OldForInHintI18nSpec for the motivating regression.
 */
class OldSuperInitHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.old_super_init"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("fires for the old `def this(x) : (x) { }` anonymous super-call syntax") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Base {
        |public:
        |  def this(x: Int) { }
        |}
        |class Sub extends Base {
        |public:
        |  def this(x: Int) : (x) {
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains(": this(...)"), s"expected the hint's `: this(...)` example, got: $msgs")
  }
}
