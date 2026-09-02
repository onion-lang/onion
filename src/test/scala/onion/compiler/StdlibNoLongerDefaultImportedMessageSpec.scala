package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Pins the exact bilingual text of `suggestion.stdlibNoLongerDefaultImported`
 * (see `StdlibNoLongerDefaultImportedHintSpec` for the end-to-end behavior),
 * independent of the JVM's default locale -- same pattern as
 * `E0067MissingReturnArticleSpec`.
 */
class StdlibNoLongerDefaultImportedMessageSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("suggestion.stdlibNoLongerDefaultImported names the class and the qualified call in English"):
    englishMessage("suggestion.stdlibNoLongerDefaultImported", "Files", "readText") shouldBe
      "hint: readText is a static member of Files, which is no longer a default import (the default static import set was narrowed to pure classes) -- qualify the call as Files::readText(...)."

  test("Japanese translation names the class and the qualified call"):
    val ja = MessageBundles.japanese
    val text = MessageBundles.format(ja, "suggestion.stdlibNoLongerDefaultImported", "Files", "readText")
    text should include("Files::readText(...)")
    text should include("Files")
    text should include("readText")
