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
    "error.semantic.lawThrew",
    // A law that cannot run, and a class whose checks never ran (#346).
    "error.semantic.lawParameterNotGeneratable",
    "error.semantic.lawClassNotLoadable"
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
    // lawFalsified carries a fourth argument: how to reproduce the run that produced the
    // counterexample. A message with an unfilled placeholder prints a literal "{3}", so
    // the argument count is part of the contract, not an implementation detail.
    val falsified = onion.compiler.toolbox.Message(
      "error.semantic.lawFalsified", Array[Any]("roundtrip", "Pt", "3, 4", "--law-seed 42 --law-samples 40"))
    assert(falsified.contains("roundtrip"))
    assert(falsified.contains("Pt"))
    assert(falsified.contains("3, 4"))
    assert(falsified.contains("--law-seed 42"))
  }

  it("leaves no unfilled placeholder in either locale") {
    val filled = Map(
      "error.semantic.exampleFailedFalse" -> Array[Any]("ex", "R"),
      "error.semantic.exampleFailedThrew" -> Array[Any]("ex", "R", "Boom"),
      "error.semantic.lawFalsified" -> Array[Any]("l", "R", "1", "seed"),
      "error.semantic.lawThrew" -> Array[Any]("l", "R", "1", "Boom", "seed"),
      "error.semantic.lawParameterNotGeneratable" -> Array[Any]("l", "R", "int[]"),
      "error.semantic.lawClassNotLoadable" -> Array[Any]("R")
    )
    for (locale <- Seq(Locale.ENGLISH, Locale.JAPANESE)) {
      val bundle = ResourceBundle.getBundle("errorMessage", locale)
      for ((key, args) <- filled) {
        val text = java.text.MessageFormat.format(bundle.getString(key), args*)
        assert(!text.matches("(?s).*\\{\\d+\\}.*"), s"$key left a placeholder unfilled in $locale: $text")
      }
    }
  }
}
