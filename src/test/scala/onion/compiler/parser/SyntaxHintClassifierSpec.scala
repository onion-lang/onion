package onion.compiler.parser

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SyntaxHintClassifierSpec extends AnyFunSpec with Matchers {
  describe("SyntaxHintClassifier") {
    it("prefers extension-declaration advice over primitive static-call advice") {
      val hint = classify(
        found = "Int",
        context = "Int.doubled(): Int {",
        sourceLine = "fun Int.doubled(): Int {"
      )

      hint.messageKey shouldBe "error.parsing.hint.fun_extension_declaration"
      hint.arguments shouldBe Seq("Int", "doubled")
    }

    it("prefers foreach-parentheses advice over generic old-for-in advice") {
      val hint = classify(
        found = "in",
        expected = "\",\"",
        sourceLine = "foreach (item in items) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.foreach_parens"
      hint.arguments shouldBe empty
    }

    it("prefers destructuring-foreach advice over generic C-style-for advice") {
      val hint = classify(
        found = ",",
        sourceLine = "for (key, value) in entries {"
      )

      hint.messageKey shouldBe "error.parsing.hint.for_each_destructure"
      hint.arguments shouldBe Seq("key, value", "entries")
    }

    it("captures Java-style import alias arguments in Onion order") {
      val hint = classify(
        found = "=",
        expected = "\".\"",
        sourceLine = "Alias = java.lang.String;"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_import_alias"
      hint.arguments shouldBe Seq("java.lang.String", "Alias")
    }

    it("distinguishes Java-style methods from the nearby constructor fallback") {
      val cases = Seq(
        ("Widget build(", "error.parsing.hint.java_style_method"),
        ("Widget(", "error.parsing.hint.java_style_constructor")
      )

      cases.foreach { case (sourceLine, expectedMessageKey) =>
        val hint = classify(
          found = "(",
          expected = "\"def\"",
          sourceLine = sourceLine
        )

        hint.messageKey shouldBe expectedMessageKey
        hint.arguments shouldBe empty
      }
    }

    it("recognizes an old arrow in a parenthesized trailing lambda") {
      val hint = classify(
        found = "{",
        context = "{ (item) =>"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_trailing_arrow"
      hint.arguments shouldBe empty
    }

    it("keeps the reserved-word and missing-block fallbacks") {
      val reserved = classify(found = "class", expected = "<ID>")
      val block = classify(found = ")", expected = "\"{\"")

      reserved.messageKey shouldBe "error.parsing.hint.reserved_word_identifier"
      reserved.arguments shouldBe Seq("class")
      block.messageKey shouldBe "error.parsing.hint.block_expected"
      block.arguments shouldBe empty
    }

    it("recognizes a C-style cast") {
      val hint = classify(
        found = "o",
        expected = "<EOL>",
        sourceLine = "    val s: String = (String) o"
      )

      hint.messageKey shouldBe "error.parsing.hint.c_style_cast"
      hint.arguments shouldBe Seq("String")
    }

    it("recognizes a C-style cast of a generic type") {
      val hint = classify(
        found = "coll",
        expected = "<EOL>",
        sourceLine = "val xs = (List[String]) coll"
      )

      hint.messageKey shouldBe "error.parsing.hint.c_style_cast"
      hint.arguments shouldBe Seq("List[String]")
    }

    it("recognizes a Python/Rust/TypeScript-style `-> Type` return type arrow") {
      val hint = classify(
        found = "->",
        expected = "\"=\"",
        sourceLine = "  def bar(x: Int) -> Int {"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_return_arrow"
      hint.arguments shouldBe Seq("bar", "Int")
    }

    it("does not confuse the real `(expr as Type)` cast with a C-style cast") {
      SyntaxHintClassifier.classify(
        found = "as",
        expected = "\")\"",
        context = "",
        sourceLine = "val s: String = (o as String)"
      ) shouldBe None
    }

    it("returns no hint when no classification rule matches") {
      SyntaxHintClassifier.classify(
        found = ")",
        expected = "<EOF>",
        context = "",
        sourceLine = "value)"
      ) shouldBe None
    }
  }

  private def classify(
    found: String,
    expected: String = "",
    context: String = "",
    sourceLine: String = ""
  ): SyntaxHint =
    SyntaxHintClassifier
      .classify(found, expected, context, sourceLine)
      .getOrElse(fail("expected a syntax hint"))
}
