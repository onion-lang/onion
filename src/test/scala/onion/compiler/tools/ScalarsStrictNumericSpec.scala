package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `Scalars::read`/`coerce` exist to be stricter than the JDK's own numeric parsers at a
 * boundary (see the class doc, which calls out `Boolean.parseBoolean`'s looseness by
 * name). `Double.valueOf`/`Float.valueOf` have an analogous looseness -- they accept
 * `NaN`, `Infinity`/`-Infinity`, and a trailing `d`/`D`/`f`/`F` type suffix, none of which
 * a user typed into a form field, a CSV cell, or a config value meant -- that `read` was
 * passing straight through unguarded (#2147).
 */
class ScalarsStrictNumericSpec extends AnyFunSpec {

  private def readsBad(tag: String, text: String): Unit = {
    val outcome = onion.Scalars.read(tag, text, null, "x")
    assert(!outcome.isOk(), s"""Scalars::read("$tag", "$text", ...) should be a defect, was ${outcome}""")
  }

  private def readsOk(tag: String, text: String): Unit = {
    val outcome = onion.Scalars.read(tag, text, null, "x")
    assert(outcome.isOk(), s"""Scalars::read("$tag", "$text", ...) should parse, was ${outcome}""")
  }

  describe("Scalars::read(\"Double\", ...)") {
    it("rejects NaN") { readsBad("Double", "NaN") }
    it("rejects Infinity") { readsBad("Double", "Infinity") }
    it("rejects -Infinity") { readsBad("Double", "-Infinity") }
    it("rejects a Java double-literal suffix") { readsBad("Double", "1.0d") }
    it("rejects an uppercase Java double-literal suffix") { readsBad("Double", "1.0D") }
    it("still accepts a plain decimal") { readsOk("Double", "3.14") }
    it("still accepts a plain integer-valued decimal") { readsOk("Double", "42") }
  }

  describe("Scalars::read(\"Float\", ...)") {
    it("rejects NaN") { readsBad("Float", "NaN") }
    it("rejects Infinity") { readsBad("Float", "Infinity") }
    it("rejects a Java float-literal suffix") { readsBad("Float", "2.5f") }
    it("rejects an uppercase Java float-literal suffix") { readsBad("Float", "2.5F") }
    it("still accepts a plain decimal") { readsOk("Float", "2.5") }
  }

  describe("Scalars::coerce(\"Double\"/\"Float\", <String>, ...)") {
    it("rejects NaN routed through coerce's String branch") {
      val outcome = onion.Scalars.coerce("Double", "NaN", null, "x")
      assert(!outcome.isOk(), outcome.toString)
    }
    it("still accepts a plain decimal routed through coerce's String branch") {
      val outcome = onion.Scalars.coerce("Float", "2.5", null, "x")
      assert(outcome.isOk(), outcome.toString)
    }
  }
}
