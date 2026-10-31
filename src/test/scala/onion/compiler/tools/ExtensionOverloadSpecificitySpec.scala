package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Extension overloads follow the class-method rule: the most specific applicable
 * overload wins, an argument boxes into an `Object` parameter, and a real ambiguity
 * is one E0006 with no E0005 after it (#2446).
 */
class ExtensionOverloadSpecificitySpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runMain(decls: String, body: String): Shell.Result =
    shell.run(
      s"""
         |$decls
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    return $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("extension overload resolution") {
    it("picks the most specific primitive overload") {
      val result = runMain(
        """extension String {
          |  def ex(c: Char): String = "char:" + c
          |  def ex(l: Long): String = "long:" + l
          |}""".stripMargin,
        """"x".ex('A') + "," + "x".ex(5L) + "," + "x".ex(5)"""
      )
      assert(Shell.Success("char:A,long:5,long:5") == result)
    }

    it("picks a List parameter over an Object one") {
      val result = runMain(
        """extension String {
          |  def ey(xs: List[String]): String = "list"
          |  def ey(o: Object): String = "obj"
          |}""".stripMargin,
        """"x".ey(["a"]) + "," + "x".ey("s")"""
      )
      assert(Shell.Success("list,obj") == result)
    }

    it("boxes a primitive argument into an Object parameter") {
      val result = runMain(
        """extension String {
          |  def ez(o: Object): String = "obj:" + o
          |}""".stripMargin,
        """"x".ez(3)"""
      )
      assert(Shell.Success("obj:3") == result)
    }

    it("reports a real ambiguity once, as E0006") {
      val codes = errorCodes(
        """extension String {
          |  def ew(a: String, b: Object): String = "so"
          |  def ew(a: Object, b: String): String = "os"
          |}
          |IO::println("x".ew("p", "q"))
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0006")))
    }

    it("reports unrelated receivers with equal parameters as E0006, in either order") {
      val decls = Seq(
        """extension I { def f(): String = "I" }""",
        """extension J { def f(): String = "J" }"""
      )
      decls.permutations.foreach { order =>
        val codes = errorCodes(
          s"""interface I { }
             |interface J { }
             |class C conforms I, J { public: def this { } }
             |${order.mkString("\n")}
             |IO::println(new C().f())
             |""".stripMargin
        )
        assert(codes == Seq(Some("E0006")), order)
      }
    }

    it("reports a raw and an applied receiver with equal parameters as E0006, in either order") {
      val decls = Seq(
        """extension List { def tag(o: Object): String = "raw" }""",
        """extension List[Int] { def tag(o: Object): String = "int" }"""
      )
      decls.permutations.foreach { order =>
        val codes = errorCodes(
          s"""${order.mkString("\n")}
             |val xs: List[Int] = [1, 2]
             |IO::println(xs.tag(1))
             |""".stripMargin
        )
        assert(codes == Seq(Some("E0006")), order)
      }
    }

    it("still lets the more specific receiver win when the parameters are equal") {
      val result = runMain(
        """extension CharSequence { def g(o: Object): String = "cs" }
          |extension String { def g(o: Object): String = "str" }""".stripMargin,
        """"x".g(1)"""
      )
      assert(Shell.Success("str") == result)
    }

    it("still reports E0005 when no overload applies") {
      val codes = errorCodes(
        """extension String {
          |  def ex(c: Char): String = "char:" + c
          |}
          |IO::println("x".ex("no"))
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0005")))
    }
  }
}
