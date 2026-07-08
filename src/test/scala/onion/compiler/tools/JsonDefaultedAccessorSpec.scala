package onion.compiler.tools

import onion.tools.Shell

/**
 * Json's defaulted accessors (getIntOr/getStringOr/...) return a primitive with
 * an explicit fallback when the key is missing or wrong-typed, so a missing key
 * is handled without the NullPointerException that assigning the boxed-nullable
 * getInt(...) to a non-null primitive would throw.
 */
class JsonDefaultedAccessorSpec extends AbstractShellSpec {
  it("returns the default for a missing key instead of NPE-ing") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val obj = Json::parse("{}")
        |   return Json::getIntOr(obj, "missing", 42)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(42) == result)
  }

  it("returns the present value") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   val obj = Json::parse("{\"name\": \"kota\", \"n\": 7}")
        |   return Json::getStringOr(obj, "name", "anon") + ":" + Json::getIntOr(obj, "n", 0)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("kota:7") == result)
  }
}
