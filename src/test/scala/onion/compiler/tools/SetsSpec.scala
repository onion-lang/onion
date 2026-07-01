package onion.compiler.tools

import onion.tools.Shell

class SetsSpec extends AbstractShellSpec {
  describe("Sets library") {

    it("computes union") {
      val result = shell.run(
        """
          |import { onion.Sets }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a = Sets::of(1, 2, 3)
          |    val b = Sets::of(2, 3, 4)
          |    return Sets::union(a, b).size()
          |  }
          |}
          |""".stripMargin,
        "SetsUnion.on",
        Array()
      )
      assert(Shell.Success(4) == result)
    }

    it("computes intersection") {
      val result = shell.run(
        """
          |import { onion.Sets }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a = Sets::of(1, 2, 3)
          |    val b = Sets::of(2, 3, 4)
          |    return Sets::intersection(a, b).size()
          |  }
          |}
          |""".stripMargin,
        "SetsIntersection.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("computes difference") {
      val result = shell.run(
        """
          |import { onion.Sets }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a = Sets::of(1, 2, 3)
          |    val b = Sets::of(2, 3, 4)
          |    return Sets::difference(a, b).size()
          |  }
          |}
          |""".stripMargin,
        "SetsDifference.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }
}
