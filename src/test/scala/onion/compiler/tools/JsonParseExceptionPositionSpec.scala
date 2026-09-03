package onion.compiler.tools

import onion.tools.Shell

/**
 * `Json.JsonParseException` carries the character offset where parsing gave up via
 * `getPosition()`, in addition to the usual `message()` — documented in
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md but never exercised by a
 * `shell.run` test case.
 */
class JsonParseExceptionPositionSpec extends AbstractShellSpec {
  it("getPosition() reports the offset where parsing failed") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   try {
        |     Json::parse("{bad json")
        |     return -1
        |   } catch e: Json.JsonParseException {
        |     return e.getPosition()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(1) == result)
  }

  it("message() and getPosition() are both usable on the caught exception") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   try {
        |     Json::parse("not json")
        |     return "no exception"
        |   } catch e: Json.JsonParseException {
        |     return e.message() + "|" + e.getPosition()
        |   }
        | }
      """.stripMargin, "None", Array())
    assert(result match {
      case Shell.Success(s: String) => s.contains("|0")
      case _ => false
    })
  }
}
