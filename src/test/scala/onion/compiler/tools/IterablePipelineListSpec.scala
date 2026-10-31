package onion.compiler.tools

import onion.tools.Shell

/**
 * #2496: `map`/`filter` on an `Iterable` that is neither a `List` nor a `Set` (a range,
 * `map.values()`, a Java `Iterable`) used to be typed `Iterable[T]`, so `.size`, `[i]` and
 * `.take(n)` on the result did not compile, though the value was already a list. They are
 * typed `List[T]` now.
 */
class IterablePipelineListSpec extends AbstractShellSpec {

  private def run(body: String): Shell.Result =
    shell.run(
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n",
      "IterablePipelineList.on",
      Array())

  describe("map/filter on a non-List Iterable return a List") {
    it("(1..10).filter { ... } has a size and an index") {
      assert(run(
        """
          |val evens = (1..10).filter { x -> x % 2 == 0 }
          |return evens.size + ":" + evens[0] + ":" + evens
          |""".stripMargin) == Shell.Success("5:2:[2, 4, 6, 8, 10]"))
    }

    it("(1..5).map { ... } chains into a list pipeline") {
      assert(run(
        """
          |val sq: List[Int] = (1..5).map { x -> x * x }
          |return "" + (1..5).map { x -> x * x }.take(2) + sq.size
          |""".stripMargin) == Shell.Success("[1, 4]5"))
    }

    it("map.values() pipelines give a List") {
      assert(run(
        """
          |val m = ["a": 1, "b": 2, "c": 3]
          |val big: List[Int] = m.values().filter { v -> v > 1 }
          |return "" + big + big.size + m.values().map { v -> v * 10 }[2]
          |""".stripMargin) == Shell.Success("[2, 3]230"))
    }

    it("the result still fits an Iterable slot") {
      assert(run(
        """
          |val it: Iterable[Int] = (1..3).map { x -> x + 1 }
          |val sb = new StringBuilder()
          |foreach x in it { sb.append(x) }
          |return sb.toString()
          |""".stripMargin) == Shell.Success("234"))
    }
  }
}
