package onion.compiler.tools


import onion.tools.Shell
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ShellSpec extends FeatureSpec with GivenWhenThen with BeforeAndAfter {
  feature("Shell") {
    scenario("shows 'Hello, World'") {
      val resultHelloWorld = Shell(Seq()).run(
        """
          |class HelloWorld {
          |public:
          |  static def main(args :String[]): String {
          |    return "Hello, World";
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, World") === resultHelloWorld)
    }
  }
}