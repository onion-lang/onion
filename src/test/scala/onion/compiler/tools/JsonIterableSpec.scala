package onion.compiler.tools

import onion.tools.Shell

/**
 * `Json::stringify` wrote a range (`1..3`, an `Iterable[Int]` that is not a `Collection`)
 * as the quoted string "Range(1..<4)" (#3010). Any `Iterable` is now written as an array.
 */
class JsonIterableSpec extends AbstractShellSpec {
  it("writes a range literal as a JSON array") {
    val result = shell.run(
      """
        |import { onion.Json; }
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    return Json::stringify(1..3) + " " + Json::stringify(["r": 1..<3])
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success("[1,2,3] {\"r\":[1,2]}") == result)
  }

  it("still writes a java.nio.file.Path as a string, not as its segments") {
    val result = shell.run(
      """
        |import { onion.Json; java.nio.file.Paths; }
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    return Json::stringify(Paths::get("a", "b"))
        |  }
        |}
        |""".stripMargin, "None", Array())
    assert(Shell.Success("\"a/b\"") == result)
  }
}
