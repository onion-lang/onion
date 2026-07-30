package onion.compiler.tools

import onion.tools.Shell

/**
 * Execution coverage for `onion.Json` accessors that are documented in
 * docs/reference/stdlib.md (the `getString`/`getInt`/`getLong`/`getDouble`/
 * `getFloat`/`getBoolean`/`getShort`/`getByte` family and their `*Or`
 * defaulted counterparts) but, unlike `getInt`/`getDouble`/`getBoolean`/
 * `getIntOr`/`getStringOr`, were never invoked by a `shell.run` test case:
 * `getLong`, `getFloat`, `getShort`, `getByte`, `getLongOr`, `getDoubleOr`,
 * `getFloatOr`, `getBooleanOr`.
 */
class JsonAccessorCoverageSpec extends AbstractShellSpec {

  it("getLong() converts a number to Long") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Long {
        |    val obj = Json::parse("{\"count\": 42}")
        |    val count: Long = Json::getLong(obj, "count")
        |    return count
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(42L) == result)
  }

  it("getFloat() converts a number to Float") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Float {
        |    val obj = Json::parse("{\"pi\": 3.5}")
        |    val pi: Float = Json::getFloat(obj, "pi")
        |    return pi
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(3.5f) == result)
  }

  it("getShort() converts a number to Short") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val obj = Json::parse("{\"n\": 100}")
        |    val n: Short = Json::getShort(obj, "n")
        |    return n as Int
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(100) == result)
  }

  it("getByte() converts a number to Byte") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val obj = Json::parse("{\"n\": 7}")
        |    val n: Byte = Json::getByte(obj, "n")
        |    return n as Int
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(7) == result)
  }

  it("getLongOr() returns the value when present and the default when missing") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val obj = Json::parse("{\"n\": 5}")
        |    return Long::toString(Json::getLongOr(obj, "n", 99L)) + "|" + Long::toString(Json::getLongOr(obj, "missing", 99L))
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("5|99") == result)
  }

  it("getDoubleOr() returns the value when present and the default when missing") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val obj = Json::parse("{\"pi\": 3.5}")
        |    return Double::toString(Json::getDoubleOr(obj, "pi", 0.0)) + "|" + Double::toString(Json::getDoubleOr(obj, "missing", 1.5))
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("3.5|1.5") == result)
  }

  it("getFloatOr() returns the value when present and the default when missing") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val obj = Json::parse("{\"pi\": 2.5}")
        |    return Float::toString(Json::getFloatOr(obj, "pi", 0.0f)) + "|" + Float::toString(Json::getFloatOr(obj, "missing", 1.5f))
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("2.5|1.5") == result)
  }

  it("getBooleanOr() returns the value when present and the default when missing") {
    val result = shell.run(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val obj = Json::parse("{\"active\": true}")
        |    return Boolean::toString(Json::getBooleanOr(obj, "active", false)) + "|" + Boolean::toString(Json::getBooleanOr(obj, "missing", false))
        |  }
        |}
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("true|false") == result)
  }
}
