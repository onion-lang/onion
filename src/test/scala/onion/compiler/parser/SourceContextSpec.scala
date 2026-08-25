package onion.compiler.parser

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SourceContextSpec extends AnyFunSpec with Matchers {
  describe("SourceContext.at") {
    it("extracts hint context and the full source line from a 1-based position") {
      val source =
        """first line
          |  switch (value) {
          |last line""".stripMargin

      val result = SourceContext.at(source, line = 2, column = 3)

      result.context shouldBe "switch (value) {\nlast line"
      result.sourceLine shouldBe "  switch (value) {"
    }

    it("bounds hint context without truncating the source line") {
      val longLine = "x" * 205

      val result = SourceContext.at(s"header\n$longLine", line = 2, column = 1)

      result.context shouldBe "x" * 200
      result.sourceLine shouldBe longLine
    }

    it("returns empty text when the requested line is past the end of the source") {
      val result = SourceContext.at("first\nsecond", line = 3, column = 1)

      result.context shouldBe empty
      result.sourceLine shouldBe empty
    }
  }
}
