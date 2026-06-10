package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #135: nested class references (Map.Entry resolves to
 * java.util.Map$Entry through imports) and dotted type names in type
 * positions like foreach.
 */
class NestedClassSpec extends AbstractShellSpec {

  describe("Nested class references") {
    it("uses Map.Entry as the foreach element type") {
      val result = shell.run(
        """
          |import { java.util.Map }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a": 1, "b": 2]
          |    var s = ""
          |    foreach e: Map.Entry in m.entrySet() {
          |      s = s + e.getKey() + "=" + e.getValue() + ";"
          |    }
          |    return s
          |  }
          |}
          |""".stripMargin,
        "MapEntryForeach.on",
        Array()
      )
      assert(Shell.Success("a=1;b=2;") == result)
    }
  }
}
