package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for #132 items: void (side-effect-only) lambdas against generic
 * SAM returns, and primitive type-argument boxing in inference
 * (Future::async of an Int lambda is Future[Integer], not Future[int]).
 */
class FutureErgonomicsSpec extends AbstractShellSpec {

  describe("Future with lambdas") {
    it("accepts a side-effect-only lambda in async") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuffer()
          |    val f = Future::async(() -> sb.append("ran"))
          |    f.await()
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "VoidLambdaAsync.on",
        Array()
      )
      assert(Shell.Success("ran") == result)
    }

    it("boxes primitive results into the Future's type argument") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f = Future::async(() -> 21 * 2)
          |    val sb = new StringBuffer()
          |    val r = f.await()
          |    f.onSuccess { v => sb.append("got " + v) }
          |    return sb.toString() + ":" + r
          |  }
          |}
          |""".stripMargin,
        "FutureBoxedTypeArg.on",
        Array()
      )
      assert(Shell.Success("got 42:42") == result)
    }

    it("chains map with trailing lambdas") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val g = Future::async(() -> 10).map { x => (x as Int) + 5 }
          |    return (g.await() as Int)
          |  }
          |}
          |""".stripMargin,
        "FutureMapChain.on",
        Array()
      )
      assert(Shell.Success(15) == result)
    }
  }
}
