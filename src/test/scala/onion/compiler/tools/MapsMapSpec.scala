package onion.compiler.tools

import onion.tools.Shell

/**
 * `m.map { k, v -> ... }` -- turning each entry of a Map into one value -- was E0005
 * ("did you mean: get"); the operation existed only as `toList(m) { k, v -> ... }`, a
 * name nobody guesses. `map` on a Map now does it, returning a List in iteration order,
 * as Kotlin's `Map.map` does.
 */
class MapsMapSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    val m = ["a": 1, "b": 2, "c": 3]
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("Map.map") {
    it("maps each entry to a List element, in iteration order") {
      assert(eval("""return "" + m.map { k, v -> k + "=" + v }""") == Shell.Success("[a=1, b=2, c=3]"))
    }
    it("infers the element type for further List calls") {
      assert(eval("""return "" + m.map { k, v -> v * 10 }.sum()""") == Shell.Success("60.0"))
    }
    it("works as a static call") {
      assert(eval("""return "" + Maps::map(m) { k, v -> k }""") == Shell.Success("[a, b, c]"))
    }
    it("reports E0101 for a one-parameter lambda") {
      assert(eval("""return "" + m.map { e -> e }""") == Shell.Failure(-1))
    }
  }
}
