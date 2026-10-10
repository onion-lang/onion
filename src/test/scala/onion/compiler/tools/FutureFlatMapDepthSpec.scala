package onion.compiler.tools

import onion.tools.Shell

/** #2040: a recursive Future::flatMap / do[Future] chain must not overflow the stack. */
class FutureFlatMapDepthSpec extends AbstractShellSpec {

  describe("recursive Future chains") {
    it("flatMap recursion 200000 levels deep completes") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def chain(n: Int): Future[Int] {
          |    if n <= 0 { return Future::successful(0) }
          |    return Future::successful(0).flatMap((x: Int) -> Test::chain(n - 1))
          |  }
          |  static def main(args: String[]): Int {
          |    return Test::chain(200000).await()
          |  }
          |}
          |""".stripMargin,
        "DeepFlatMap.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("do[Future] recursion 200000 levels deep completes") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def chain(n: Int): Future[Int] {
          |    if n <= 0 { return Future::successful(0) }
          |    return do[Future] { x <- Future::successful(1); y <- Test::chain(n - 1); ret x + y }
          |  }
          |  static def main(args: String[]): Int {
          |    return Test::chain(200000).await()
          |  }
          |}
          |""".stripMargin,
        "DeepDoFuture.on",
        Array()
      )
      assert(Shell.Success(200000) == result)
    }
  }
}
