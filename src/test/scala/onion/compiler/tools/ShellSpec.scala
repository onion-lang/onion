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
      val resultFib5 = shell.run(
        """
          |class Fib {
          |  static def fib(n: Int): Int {
          |    if n < 2 { return 1; } else { return n * fib(n - 1); }
          |  }
          |public:
          |  static def main(args: String[]): Int {
          |    return fib(5);
          |  }
          |}
       """.stripMargin,
        "None",
        Array()
      )
      def fib(n: Int): Int = if(n < 2) 1 else n * fib(n - 1)
      assertResult(Shell.Success(fib(5)))(resultFib5)
    }
  }
}