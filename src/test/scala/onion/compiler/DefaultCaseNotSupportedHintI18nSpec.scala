package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The default-case hint (`SyntaxHintClassifier`'s `DefaultCaseLabel` case) must
 * resolve through the bilingual `error.parsing.hint.*` bundle in both locales,
 * like every other hint in that match -- see JavaStyleAnnotatedMethodHintI18nSpec
 * for the same pattern.
 */
class DefaultCaseNotSupportedHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.default_case_not_supported"
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

  it("fires for a Java/JS-style `default:` case inside a `select` block") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    select 1 {
        |    case 1: IO::println("one")
        |    default: IO::println("other")
        |    }
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("else"), s"expected the hint to mention `else`, got: $msgs")
  }
}
