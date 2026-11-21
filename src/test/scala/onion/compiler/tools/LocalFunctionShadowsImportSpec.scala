package onion.compiler.tools

import onion.tools.Shell

class LocalFunctionShadowsImportSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class T {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a local function value and a default-imported static of the same name") {
    it("calls the local `first`, not Iterables::first (#3075)") {
      val r = run(
        """    val first: (List[Int]) -> Int = (ys) -> 42
          |    return "" + first([1, 2, 3])""".stripMargin)
      assert(Shell.Success("42") == r)
    }

    it("calls the local `last`") {
      val r = run(
        """    val last: (List[Int]) -> Int = (ys) -> 99
          |    return "" + last([1, 2, 3])""".stripMargin)
      assert(Shell.Success("99") == r)
    }

    it("still calls the import when no local of that name exists") {
      val r = run("""    return "" + first([7, 8])""")
      assert(Shell.Success("7") == r)
    }
  }
}
