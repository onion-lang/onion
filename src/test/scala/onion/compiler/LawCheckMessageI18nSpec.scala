package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.util.{Locale, MissingResourceException, ResourceBundle}

/**
 * `law`/`example` compile-time check failures (E0064/E0065) are built directly in
 * LawCheckPhase rather than through SemanticErrorReporter, and used to hard-code English
 * text instead of going through the bilingual `errorMessage` bundle like every other
 * diagnostic. Both locale bundles must carry the keys LawCheckPhase resolves.
 */
class LawCheckMessageI18nSpec extends AnyFunSpec with Diagrams {
  private val keys = Seq(
    "error.semantic.exampleFailedFalse",
    "error.semantic.exampleFailedThrew",
    "error.semantic.lawFalsified",
    "error.semantic.lawThrew"
  )

  private def assertResolvable(locale: Locale): Unit = {
    val bundle = ResourceBundle.getBundle("errorMessage", locale)
    for (key <- keys) {
      try bundle.getString(key)
      catch {
        case _: MissingResourceException => fail(s"missing key '$key' for locale $locale")
      }
    }
  }

  it("resolves every LawCheckPhase message key in the English bundle") {
    assertResolvable(Locale.ENGLISH)
  }

  it("resolves every LawCheckPhase message key in the Japanese bundle") {
    assertResolvable(Locale.JAPANESE)
  }

  it("formats placeholders via onion.compiler.toolbox.Message") {
    val falsified = onion.compiler.toolbox.Message("error.semantic.lawFalsified", Array[Any]("roundtrip", "Pt", "3, 4"))
    assert(falsified.contains("roundtrip"))
    assert(falsified.contains("Pt"))
    assert(falsified.contains("3, 4"))
  }
}
