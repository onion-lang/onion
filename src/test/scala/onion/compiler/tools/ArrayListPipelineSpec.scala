package onion.compiler.tools

import onion.tools.Shell

/**
 * An array of references (most often `String[]` from `String.split` or `main`'s `args`)
 * had only `map`/`filter`/`toList` of the `List` pipeline, so `line.split(" ").distinct()`
 * failed with E0005 and the script had to spell out `.toList()` first. A `List` pipeline
 * method called on such an array now applies to the array's elements as a `List`, the
 * same way `map`/`filter` already returned a `List`.
 */
class ArrayListPipelineSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    val words: String[] = "b a c a b".split(" ")
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("List pipeline methods on a String[]") {
    it("distinct") {
      assert(eval("""return "" + words.distinct()""") == Shell.Success("[b, a, c]"))
    }
    it("groupBy with a trailing lambda") {
      assert(eval("""return "" + words.groupBy { w -> w }.get("a").size""") == Shell.Success("2"))
    }
    it("sortedBy") {
      assert(eval("""return "" + words.sortedBy { w -> w }""") == Shell.Success("[a, a, b, b, c]"))
    }
    it("join") {
      assert(eval("""return words.join("-")""") == Shell.Success("b-a-c-a-b"))
    }
    it("take, first and count") {
      assert(eval("""return "" + words.take(2) + words.first() + words.count { w -> w == "b" }""") ==
        Shell.Success("[b, a]b2"))
    }
    it("chains into further List calls") {
      assert(eval("""return "" + words.distinct().sorted()""") == Shell.Success("[a, b, c]"))
    }
    it("leaves map, filter and length as they were") {
      assert(eval("""return "" + words.map { w -> w.toUpperCase() } + words.filter { w -> w == "a" } + words.length""") ==
        Shell.Success("[B, A, C, A, B][a, a]5"))
    }
    it("still reports E0005 for a name no List has") {
      assert(eval("""return "" + words.frobnicate()""") == Shell.Failure(-1))
    }
  }
}
