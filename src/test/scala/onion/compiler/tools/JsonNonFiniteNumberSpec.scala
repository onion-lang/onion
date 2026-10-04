package onion.compiler.tools

import onion.tools.Shell

/**
 * `Json::stringify` wrote a `Double`/`Float` NaN or +/-Infinity as the bare word
 * (`NaN`, `Infinity`, `-Infinity`), which is not a JSON number -- no conformant parser,
 * `Json::parse` included, can read it back. JSON (RFC 8259) has no token for a
 * non-finite number at all, so the only values that round-trip are the ones `Json::parse`
 * itself can ever produce: `null`, a finite double, or a string. Mirroring
 * `JSON.stringify`'s well-known JS behavior, a non-finite number now stringifies as the
 * JSON literal `null` instead.
 */
class JsonNonFiniteNumberSpec extends AbstractShellSpec {
  it("stringifies a Double NaN as the JSON literal null") {
    val result = shell.run(
      """
        |import { onion.Json; }
        | static def main(args: String[]): String {
        |   return Json::stringify(0.0d / 0.0d)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("null") == result)
  }

  it("stringifies Double positive and negative infinity as the JSON literal null") {
    val result = shell.run(
      """
        |import { onion.Json; }
        | static def main(args: String[]): String {
        |   return Json::stringify(1.0d / 0.0d) + "|" + Json::stringify(-1.0d / 0.0d)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("null|null") == result)
  }

  it("stringifies a Float NaN/Infinity as the JSON literal null too") {
    val result = shell.run(
      """
        |import { onion.Json; }
        | static def main(args: String[]): String {
        |   return Json::stringify(0.0f / 0.0f) + "|" + Json::stringify(1.0f / 0.0f)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("null|null") == result)
  }

  it("leaves a finite double untouched") {
    val result = shell.run(
      """
        |import { onion.Json; }
        | static def main(args: String[]): String {
        |   return Json::stringify(3.5)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("3.5") == result)
  }

  it("round-trips a map with a NaN value through parse(stringify(...)) as a real null") {
    val result = shell.run(
      """
        |import { onion.Json; }
        | static def main(args: String[]): String {
        |   val m = ["a": 0.0d / 0.0d, "b": 3.5]
        |   val s = Json::stringify(m)
        |   val back = Json::parse(s) as Map
        |   return back["a"] + "|" + back["b"]
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("null|3.5") == result)
  }
}
