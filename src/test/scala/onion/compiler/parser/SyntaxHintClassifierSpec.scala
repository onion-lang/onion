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

    it("prefers enhanced-for advice over generic C-style-for advice") {
      val hint = classify(
        found = "s",
        sourceLine = "for (String s : list) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_enhanced_for"
      hint.arguments shouldBe Seq("s", "String", "list")
    }

    it("recognizes an enhanced-for loop over a qualified/generic element type") {
      val hint = classify(
        found = "entry",
        sourceLine = "for (Map.Entry[String, Int] entry : map.entrySet()) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_enhanced_for"
      hint.arguments shouldBe Seq("entry", "Map.Entry[String, Int]", "map.entrySet()")
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

    it("recognizes an old arrow in a standalone (non-trailing) lambda") {
      val hint = classify(
        found = "=",
        expected = "<EOF>, <EOL>, \";\"",
        context = "=> x * 2",
        sourceLine = "val f = (x) => x * 2"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_lambda_arrow"
      hint.arguments shouldBe empty
    }

    it("recognizes an old arrow in a bare-parameter standalone lambda") {
      val hint = classify(
        found = "=",
        expected = "<EOF>, <EOL>, \";\"",
        context = "=> x * 2",
        sourceLine = "val f = x => x * 2"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_lambda_arrow"
    }

    it("prefers the Ruby-style map-arrow reading when a `:` is expected next") {
      SyntaxHintClassifier.classify(
        found = "=",
        expected = "<EOL>, \":\", \",\", \"]\"",
        context = "=> 1]",
        sourceLine = "val m = [\"a\" => 1]"
      ).map(_.messageKey) should not be Some("error.parsing.hint.old_lambda_arrow")
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

    it("recognizes a Python-style `def name():` with no return type before the block") {
      val hint = classify(
        found = "\n",
        expected = "\"Boolean\", \"Byte\", \"Char\", \"Double\", ... (11 more)",
        sourceLine = "  def foo():"
      )

      hint.messageKey shouldBe "error.parsing.hint.dangling_return_type_colon"
      hint.arguments shouldBe Seq("foo")
    }

    it("does not flag a method colon that already has a same-line return type") {
      SyntaxHintClassifier.classify(
        found = "\n",
        expected = "\"Boolean\", \"Byte\", \"Char\", \"Double\", ... (11 more)",
        context = "",
        sourceLine = "  def foo(): Int {"
      ) shouldBe None
    }

    it("does not confuse the real `(expr as Type)` cast with a C-style cast") {
      SyntaxHintClassifier.classify(
        found = "as",
        expected = "\")\"",
        context = "",
        sourceLine = "val s: String = (o as String)"
      ) shouldBe None
    }

    it("recognizes a parameter missing its type annotation, not a Java-style method") {
      val hint = classify(
        found = ")",
        expected = "\":\"",
        sourceLine = "def foo(x): Int {"
      )

      hint.messageKey shouldBe "error.parsing.hint.missing_parameter_type"
      hint.arguments shouldBe Seq("x")
    }

    it("does not confuse a fully-typed parameter list with a missing-type parameter") {
      SyntaxHintClassifier.classify(
        found = ":",
        expected = "\"def\"",
        context = "",
        sourceLine = "  void foo(x: Int) {"
      ).map(_.messageKey) shouldBe Some("error.parsing.hint.java_style_method")
    }

    it("recognizes a C#/Python-style `using` resource statement") {
      val hint = classify(
        found = "r",
        sourceLine = "using r = new Res() {"
      )

      hint.messageKey shouldBe "error.parsing.hint.using_resource_statement"
      hint.arguments shouldBe Seq("r", "new Res()")
    }

    it("recognizes a Go-style `:=` short variable declaration") {
      val hint = classify(
        found = ":",
        expected = "<EOL>, \";\", <EOF>",
        sourceLine = "x := 5"
      )

      hint.messageKey shouldBe "error.parsing.hint.go_style_short_var_decl"
      hint.arguments shouldBe Seq("x", "5")
    }

    it("recognizes a Ruby-style `unless` statement") {
      val hint = classify(
        found = "unless",
        expected = "<ID>",
        sourceLine = "unless done {"
      )

      hint.messageKey shouldBe "error.parsing.hint.unless_not_supported"
      hint.arguments shouldBe empty
    }

    it("recognizes a Kotlin-style `data class` declaration") {
      val hint = classify(
        found = "class",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "data class Point(val x: Int, val y: Int)"
      )

      hint.messageKey shouldBe "error.parsing.hint.data_class_declaration"
      hint.arguments shouldBe Seq("Point", "x: Int, y: Int")
    }

    it("recognizes a JS/TS-style `constructor(...)` method") {
      val hint = classify(
        found = "constructor",
        expected = "\"abstract\", \"def\", \"final\", \"forward\", ... (13 more)",
        sourceLine = "constructor(x: Int, y: Int) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.js_style_constructor"
      hint.arguments shouldBe empty
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
