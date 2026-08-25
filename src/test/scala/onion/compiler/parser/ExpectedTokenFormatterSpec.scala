package onion.compiler.parser

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ExpectedTokenFormatterSpec extends AnyFunSpec with Matchers {
  describe("ExpectedTokenFormatter.format") {
    it("falls back when no usable expected-token sequence is available") {
      ExpectedTokenFormatter.format(null, Array.empty) shouldBe "valid token"
      ExpectedTokenFormatter.format(Array.empty, Array.empty) shouldBe "valid token"
      ExpectedTokenFormatter.format(
        Array(null, Array.empty[Int]),
        Array("unused")
      ) shouldBe "valid token"
    }

    it("uses the first token of each sequence once and in encounter order") {
      val rendered = ExpectedTokenFormatter.format(
        Array(Array(2, 3), Array(1), Array(2), null, Array.empty[Int]),
        Array("<EOF>", "\"class\"", "\"interface\"", "\"record\"")
      )

      rendered shouldBe "\"interface\" or \"class\""
    }

    it("joins one to three expected tokens with the existing separators") {
      val tokenImages = Array("<EOF>", "a", "b", "c")
      val cases = Seq(
        Array(Array(1)) -> "a",
        Array(Array(1), Array(2)) -> "a or b",
        Array(Array(1), Array(2), Array(3)) -> "a, b or c"
      )

      cases.foreach { case (sequences, expected) =>
        ExpectedTokenFormatter.format(sequences, tokenImages) shouldBe expected
      }
    }

    it("keeps the existing four-token boundary and truncates after four") {
      val tokenImages = Array("<EOF>", "a", "b", "c", "d", "e", "f")

      ExpectedTokenFormatter.format(
        Array(Array(1), Array(2), Array(3), Array(4)),
        tokenImages
      ) shouldBe "a, b, c, d, ... (0 more)"
      ExpectedTokenFormatter.format(
        Array(Array(1), Array(2), Array(3), Array(4), Array(5), Array(6)),
        tokenImages
      ) shouldBe "a, b, c, d, ... (2 more)"
    }
  }
}
