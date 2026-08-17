package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

/**
 * `error.count` is printed as the trailer after a failed compilation
 * (`DiagnosticRenderer.printErrors`, `Repl`). The English bundle used a bare
 * `"{0} errors are found."` template regardless of count, which reads as
 * "1 errors are found." for a single-error compile.
 *
 * These assertions are about the English wording, so they read the English bundle
 * directly rather than going through `onion.compiler.toolbox.Message`, which resolves
 * against the machine's default locale. Using `Message` here meant the tests only
 * happened to pass on an English machine and failed under `-Duser.language=ja` —
 * see [[MessageBundles]] for why asking for `Locale.ENGLISH` is not enough either.
 */
class ErrorCountMessageSpec extends AnyFunSpec with Diagrams {

  private def englishCount(n: Int): String =
    MessageBundles.format(MessageBundles.english, "error.count", n)

  it("uses singular wording for a single error in English") {
    assert(!englishCount(1).contains("1 errors"))
  }

  it("uses plural wording for multiple errors in English") {
    assert(englishCount(3).contains("3 errors"))
  }

  it("reports the count in Japanese too") {
    val text = MessageBundles.format(MessageBundles.japanese, "error.count", 3)
    assert(text.contains("3"))
    assert(text != englishCount(3))
  }
}
