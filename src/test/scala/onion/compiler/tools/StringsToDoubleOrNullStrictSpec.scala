package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `Strings::toDoubleOrNull` validates user input, so it follows the same rule as
 * `Scalars::read("Double", ...)` (#2147): a plain decimal or scientific-notation number,
 * not Java floating-point literal syntax (#2157).
 */
class StringsToDoubleOrNullStrictSpec extends AnyFunSpec {

  private def parses(text: String, expected: Double): Unit =
    assert(onion.Strings.toDoubleOrNull(text) == expected, s"""toDoubleOrNull("$text")""")

  private def isNull(text: String): Unit =
    assert(onion.Strings.toDoubleOrNull(text) == null, s"""toDoubleOrNull("$text") should be null""")

  describe("Strings::toDoubleOrNull") {
    it("rejects a Java double suffix") { isNull("1d"); isNull("1.5D") }
    it("rejects a Java float suffix") { isNull("1f"); isNull("2.5F") }
    it("rejects a hex float") { isNull("0x1p3") }
    it("rejects NaN and Infinity") { isNull("NaN"); isNull("Infinity"); isNull("-Infinity") }
    it("rejects empty and garbage") { isNull(""); isNull("abc"); isNull(null) }
    it("still accepts plain decimals") {
      parses("3.14", 3.14); parses("42", 42.0); parses("-0.5", -0.5)
      parses(".5", 0.5); parses("2.5e10", 2.5e10); parses("+1E-2", 0.01)
    }
    it("still trims surrounding whitespace") { parses("  3.5 ", 3.5) }
  }
}
