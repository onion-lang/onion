package onion.compiler.parser

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ControlFlowSyntaxHintsSpec extends AnyFunSpec with Matchers {
  describe("ControlFlowSyntaxHints.classify") {
    it("classifies each unsupported control-flow family member") {
      val cases = Seq(
        ("value", "switch value {", "error.parsing.hint.switch_not_supported"),
        ("when", "when value {", "error.parsing.hint.when_not_supported"),
        ("value", "match value {", "error.parsing.hint.match_not_supported"),
        (":", "default:", "error.parsing.hint.default_case_not_supported"),
        ("condition", "} elif condition {", "error.parsing.hint.elif_not_supported"),
        ("unless", "unless done {", "error.parsing.hint.unless_not_supported"),
        ("until", "until done {", "error.parsing.hint.until_not_supported"),
        ("error", "} except error: Exception {", "error.parsing.hint.except_not_supported"),
        ("Exception", "raise Exception(\"boom\")", "error.parsing.hint.python_style_raise_construct"),
        ("e", "raise e", "error.parsing.hint.python_style_raise"),
        ("foo", "await foo()", "error.parsing.hint.await_not_supported"),
        ("f", "with openResource() as f {", "error.parsing.hint.with_as_statement")
      )

      cases.foreach { case (found, sourceLine, expectedKey) =>
        ControlFlowSyntaxHints.classify(found, sourceLine).map(_.messageKey) shouldBe Some(expectedKey)
      }
    }

    it("captures the raised exception's constructor name and arguments") {
      val hint = ControlFlowSyntaxHints
        .classify(found = "Exception", sourceLine = "raise Exception(\"boom\")")
        .getOrElse(fail("expected a syntax hint"))

      hint.messageKey shouldBe "error.parsing.hint.python_style_raise_construct"
      hint.arguments shouldBe Seq("Exception", "\"boom\"")
    }

    it("captures a bare re-raised expression without inventing a constructor") {
      val hint = ControlFlowSyntaxHints
        .classify(found = "e", sourceLine = "raise e")
        .getOrElse(fail("expected a syntax hint"))

      hint.messageKey shouldBe "error.parsing.hint.python_style_raise"
      hint.arguments shouldBe Seq("e")
    }

    it("captures the awaited expression from a prefix `await expr` statement") {
      val hint = ControlFlowSyntaxHints
        .classify(found = "foo", sourceLine = "await foo()")
        .getOrElse(fail("expected a syntax hint"))

      hint.messageKey shouldBe "error.parsing.hint.await_not_supported"
      hint.arguments shouldBe Seq("foo()")
    }

    it("captures the resource expression and bound name from a `with ... as ...` block") {
      val hint = ControlFlowSyntaxHints
        .classify(found = "f", sourceLine = "with openResource() as f {")
        .getOrElse(fail("expected a syntax hint"))

      hint.messageKey shouldBe "error.parsing.hint.with_as_statement"
      hint.arguments shouldBe Seq("f", "openResource()")
    }

    it("preserves rule order inside the family") {
      ControlFlowSyntaxHints
        .classify(found = "value", sourceLine = "switch elif {")
        .map(_.messageKey) shouldBe Some("error.parsing.hint.switch_not_supported")
    }

    it("requires the parser to fail on the when keyword itself") {
      ControlFlowSyntaxHints.classify(
        found = "guard",
        sourceLine = "when value {"
      ) shouldBe None
    }

    it("returns no hint when the family does not match") {
      ControlFlowSyntaxHints.classify(
        found = "value",
        sourceLine = "if value {"
      ) shouldBe None
    }
  }
}
