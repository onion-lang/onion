package onion.compiler.tools

import onion.tools.Shell

class ForeachSpec extends AbstractShellSpec {
  describe("map entry destructuring substitutes the value type") {
    it("calls methods on a String value of Map[String, String]") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = new HashMap[String, String]()
          |    m.put("a", "xx")
          |    m.put("b", "yyy")
          |    var total = 0
          |    foreach (k, v) in m {
          |      total = total + v.length()
          |    }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("calls methods on a List value of Map[String, List]") {
      val result = shell.run(
        """
          |import {
          |  java.util.HashMap
          |  java.util.ArrayList
          |  java.util.List
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = new HashMap[String, List[String]]()
          |    val xs = new ArrayList[String]()
          |    xs.add("one")
          |    xs.add("two")
          |    m.put("g", xs)
          |    var total = 0
          |    foreach (k, v) in m {
          |      total = total + v.size()
          |    }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(2) == result)
    }
  }

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
