package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for collection pipelines: paren-less trailing lambdas plus the
 * builtin extension methods that back java.util.List/Iterable with
 * onion.Colls / onion.Iterables statics, with closure parameter types
 * inferred from the extension's signature.
 */
class CollectionPipelineSpec extends AbstractShellSpec {

  describe("Collection pipelines") {
    it("maps with a paren-less trailing lambda") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b"]
          |    return xs.map { s => s.toUpperCase() }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineMap.on",
        Array()
      )
      assert(Shell.Success("[A, B]") == result)
    }

    it("chains filter and map") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["alpha beta", "gamma", "alpha delta"]
          |    return xs.filter { s => s.contains("alpha") }.map { s => s.length() }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineChain.on",
        Array()
      )
      assert(Shell.Success("[10, 11]") == result)
    }

    it("keeps explicit Colls:: calls working") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [1, 2, 3]
          |    return Colls::map(xs, (x: Integer) -> (x as Int) * 2).toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineExplicit.on",
        Array()
      )
      assert(Shell.Success("[2, 4, 6]") == result)
    }

    it("does not shadow real instance methods") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    return xs.size()
          |  }
          |}
          |""".stripMargin,
        "PipelineNoShadow.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
    it("maps over arrays (String.split pipeline)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val parts = "a,bb,ccc".split(",")
          |    return parts.map { s => s.length() }.toString()
          |  }
          |}
          |""".stripMargin,
        "ArrayPipeline.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3]") == result)
    }
  }
}
