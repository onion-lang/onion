package onion.compiler.tools

import onion.tools.Shell

class ForeachSpec extends AbstractShellSpec {
  describe("Cat") {
    it("shows result of cat using foreach") {
      val resultCat = shell.run(
        """
          |class Cat {
          |public:
          |  static def main(args: String[]): String {
          |    val list: List = ["A", "B", "C", "D"]
          |    var result: String = ""
          |    foreach s:String in list {
          |      result = result + s;
          |    }
          |    return result
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      def cat(list: List[String]): String = list.mkString
      val answer = cat(List("A", "B", "C", "D"))
      assert(Shell.Success(answer) == resultCat)
    }
  }

}
