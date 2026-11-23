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

class JsonValueAsIntSpec extends AbstractShellSpec {
  it("asInt/asLong give 0 for a fraction or an out-of-range number, not a truncated or wrapped one (#2995)") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   val v = Json::value("{\"n\": 3.7, \"big\": 10000000000, \"ok\": 7, \"whole\": 4.0, \"bigL\": 10000000000, \"frac\": 1.5}")
        |   return "" + v.get("n").asInt() + ":" + v.get("big").asInt() + ":" + v.get("ok").asInt() + ":" + v.get("whole").asInt() +
        |     ":" + v.get("bigL").asLong() + ":" + v.get("frac").asLong() + ":" + v.get("none").asInt()
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("0:0:7:4:10000000000:0:0") == result)
  }
}
