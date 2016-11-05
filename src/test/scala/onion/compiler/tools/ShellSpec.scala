package onion.compiler.tools


import onion.tools.Shell
import org.scalatest._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ShellSpec extends FunSpec with DiagrammedAssertions {
  describe("Shell") {
    val shell = Shell(Seq())
    it("shows 'Hello, World'") {
      val resultHelloWorld = shell.run(
        """
          |class HelloWorld {
          |public:
          |  static def main(args: String[]): String {
          |    return "Hello, World";
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, World") == resultHelloWorld)
    }
    it("shows result of fib(5)") {
      val resultFac5 = shell.run(
        """
          |class Fib {
          |  static def factorial(n: Int): Int {
          |    if n < 2 { return 1; } else { return n * factorial(n - 1); }
          |  }
          |public:
          |  static def main(args: String[]): Int {
          |    return factorial(5);
          |  }
          |}
       """.stripMargin,
        "None",
        Array()
      )
      def factorial(n: Int): Int = if(n < 2) 1 else n * factorial(n - 1)
      val answer = factorial(5)
      assert(Shell.Success(answer) == resultFac5)
    }

    it("shows result of cat using foreach") {
      val resultCat = shell.run(
        """
          |class Cat {
          |public:
          |  static def main(args: String[]): String {
          |    list = ["A", "B", "C", "D"];
          |    result = "";
          |    foreach s:String in list {
          |      result = result + s;
          |    }
          |    return result;
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

    it("a function with body is a expression") {
      val resultExpressionBody = shell.run(
        """
          |class ExpressionBody {
          |public:
          |  static def main(args: String[]): String = "ExpressionBody";
          |}
        """.stripMargin,
         "None",
         Array()
      )
      assert(Shell.Success("ExpressionBody") == resultExpressionBody)
    }

  }
}