package onion.tools.lsp

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * The scanner that stops rename from editing comments and string literals.
 *
 * Every case here names an occurrence of `count` and asserts whether that offset is code.
 * The interpolation case is the one that makes the scanner worth having rather than a
 * blunt "skip anything between quotes": `"n=#{count}"` contains a real reference.
 */
class SourceRegionsSpec extends AnyFunSuite with Matchers:

  /** Whether the occurrence of `needle` at the nth position is classified as code. */
  private def codeAt(source: String, needle: String, occurrence: Int = 0): Boolean =
    val mask = SourceRegions.codeMask(source)
    var index = -1
    var found = -1
    for (_ <- 0 to occurrence) do
      found = source.indexOf(needle, index + 1)
      assert(found >= 0, s"only ${occurrence} occurrences of $needle in: $source")
      index = found
    SourceRegions.isCode(mask, found)

  test("a plain identifier is code"):
    codeAt("val count = 1", "count") shouldBe true

  test("an identifier inside a line comment is not"):
    codeAt("val n = 1 // count starts at zero", "count") shouldBe false

  test("an identifier inside a block comment is not"):
    codeAt("/* count of items */ val n = 1", "count") shouldBe false

  test("an unterminated block comment swallows the rest of the file"):
    codeAt("val n = 1 /* count", "count") shouldBe false

  test("an identifier inside a string literal is not"):
    codeAt("""val s = "count of items"""", "count") shouldBe false

  test("an identifier inside #{} interpolation IS code, because it is a real reference"):
    // This is the case a blunt "skip everything in quotes" gets wrong: renaming `count`
    // must rewrite this occurrence, or the program stops compiling.
    codeAt("""val s = "n=#{count}"""", "count") shouldBe true

  test("text on either side of an interpolation is still not code"):
    val source = """val s = "count #{count} count""""
    codeAt(source, "count", 0) shouldBe false   // before the interpolation
    codeAt(source, "count", 1) shouldBe true    // inside it
    codeAt(source, "count", 2) shouldBe false   // after it

  test("an escaped quote does not end the string early"):
    codeAt("""val s = "a \" count" """, "count") shouldBe false

  test("the body of a scheme-prefixed raw literal is not code"):
    codeAt("""val p = re"count\d+"""", "count") shouldBe false

  test("a raw literal's backslash does not end it, so what follows stays inside"):
    // `\` is not an escape for the value in a raw literal, but it still does not
    // terminate the literal -- so `count` after it is part of the body.
    codeAt("""val p = re"\d+ count"""", "count") shouldBe false

  test("the scheme prefix itself is code, since it is a function name"):
    codeAt("""val p = re"\d+"""", "re") shouldBe true

  test("an identifier inside a character literal is not"):
    codeAt("""val c = 'c' // count""", "count") shouldBe false

  test("a division is not mistaken for a comment"):
    codeAt("val count = a / b", "count") shouldBe true

  test("code after a closed comment is code again"):
    codeAt("/* note */ val count = 1", "count") shouldBe true

  test("code after a closed string is code again"):
    codeAt("""val s = "x"; val count = 1""", "count") shouldBe true

  test("nested braces inside an interpolation do not end it early"):
    val source = """val s = "#{ xs.map { x => x } }"; val count = 1"""
    codeAt(source, "count") shouldBe true
