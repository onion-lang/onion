package onion.compiler.tools

import onion.tools.Shell

/**
 * Safe indexing xs?[i]: yields null when the receiver is null, the element
 * otherwise. Works for arrays (primitive elements box) and collections
 * (routes through get()), and chains with ?. / ?:.
 */
class SafeIndexingSpec extends AbstractShellSpec {

  describe("Safe indexing") {
    it("yields null for a null list and the element otherwise") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val none: List[String]? = null
          |    val xs: List[String]? = ["a", "b"]
          |    return "" + none?[0] + "," + xs?[1]
          |  }
          |}
          |""".stripMargin,
        "SafeIndexList.on",
        Array()
      )
      assert(Shell.Success("null,b") == result)
    }

    it("yields null for a null array and boxes primitive elements") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val none: Int[]? = null
          |    val arr: Int[] = new Int[3]
          |    arr[1] = 42
          |    val maybe: Int[]? = arr
          |    return "" + none?[0] + "," + maybe?[1]
          |  }
          |}
          |""".stripMargin,
        "SafeIndexArray.on",
        Array()
      )
      assert(Shell.Success("null,42") == result)
    }

    it("chains with safe calls") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sarr: String[] = new String[1]
          |    sarr[0] = "deep"
          |    val maybe: String[]? = sarr
          |    val none: String[]? = null
          |    return "" + maybe?[0]?.length() + "," + none?[0]?.length()
          |  }
          |}
          |""".stripMargin,
        "SafeIndexChain.on",
        Array()
      )
      assert(Shell.Success("4,null") == result)
    }

    it("rejects index assignment through a nullable receiver instead of crashing") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[]? = new Int[3]
          |    arr[1] = 42
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "NullableIndexAssign.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects member assignment through a nullable receiver instead of crashing") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  var v: String
          |  def this { this.v = "" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Box? = new Box()
          |    b.v = "x"
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "NullableMemberAssign.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
