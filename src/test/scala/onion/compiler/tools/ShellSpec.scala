package onion.compiler.tools


import onion.tools.Shell
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ShellSpec extends FeatureSpec with GivenWhenThen with BeforeAndAfter{
  feature("Shell") {
    val shell = Shell(Seq())
    scenario("shows 'Hello, World'") {
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
      assertResult(Shell.Success("Hello, World"))(resultHelloWorld)
    }
    scenario("shows result of fib(5)") {
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
      assertResult(Shell.Success(factorial(5)))(resultFac5)
    }
    scenario("shows result of cat using foreach") {
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
      assertResult(Shell.Success(cat(List("A", "B", "C", "D"))))(resultCat)
    }
  }
}