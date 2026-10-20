package onion.compiler.tools

import onion.tools.Shell

/**
 * `aSet.toList()` -- `keySet().toList()`, `entrySet().toList()`, any `Set` -- failed with
 * E0006 "applicable for both Colls.toList() and Iterables.toList()" plus a stray E0005,
 * because both builtin extensions accept a `Set` and the no-closure call path treated any
 * two applicable candidates as ambiguous. Like Java overload resolution, the one whose
 * receiver type is more specific (`toList(Set)` over `toList(Iterable)`) now wins.
 */
class MostSpecificBuiltinExtensionSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("toList on a Set") {
    it("a Set built by Colls::toSet") {
      assert(eval("""return "" + Colls::toSet(["a"]).toList()""") == Shell.Success("[a]"))
    }
    it("a Map's keySet()") {
      assert(eval("""return "" + ["a": 1].keySet().toList()""") == Shell.Success("[a]"))
    }
    it("a Map's entrySet(), chaining into a List call") {
      assert(eval("""return "" + ["a": 1, "b": 5].entrySet().toList().maxBy { e -> e.getValue() }.getKey()""") ==
        Shell.Success("b"))
    }
    it("a declared java.util.Set") {
      assert(eval("""val s: java.util.Set[String] = new java.util.HashSet[String]()
                    |s.add("x")
                    |return "" + s.toList()""".stripMargin) == Shell.Success("[x]"))
    }
  }
}
