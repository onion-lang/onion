package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A lambda argument that fits several constructors, none most specific, is an ambiguous
 * constructor call (E0022), as javac says and as the same overloads on a method say (E0006).
 * Before, it was E0052 "lambda parameter must specify a type", and adding the type did not
 * help (#2950).
 */
class AmbiguousLambdaConstructorSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val imports = "import { java.util.function.Function; java.util.function.UnaryOperator }\n"

  private val two =
    """class Two {
      |public:
      |  val tag: String
      |  def this(f: Function[String, String], g: Function[String, String]) { this.tag = "FF" }
      |  def this(f: UnaryOperator[String], g: Function[String, String]) { this.tag = "UF" }
      |  def this(f: Function[String, String], g: UnaryOperator[String]) { this.tag = "FU" }
      |}
      |""".stripMargin

  private val mix =
    """class Mix {
      |public:
      |  val tag: String
      |  def this(s: String, f: Function[String, String]) { this.tag = "SF" }
      |  def this(s: Object, f: UnaryOperator[String]) { this.tag = "OU" }
      |}
      |""".stripMargin

  describe("a lambda that fits several constructors, none most specific") {
    it("is E0022 for two lambda arguments, not E0052") {
      assert(errorCodes(imports + two + """IO::println(new Two((a) -> a + "1", (b) -> b + "2").tag)""") ==
        Seq(Some("E0022")))
    }
    it("is E0022 when a plain argument and a lambda pull different ways") {
      assert(errorCodes(imports + mix + """IO::println(new Mix("m", (a) -> a + "!").tag)""") ==
        Seq(Some("E0022")))
    }
    it("is E0022 when the lambda fits neither constructor") {
      val bx =
        """class Bx {
          |public:
          |  def this(f: Function[String, Integer]) { }
          |  def this(f: UnaryOperator[String]) { }
          |}
          |""".stripMargin
      assert(errorCodes(imports + bx + """new Bx((s) -> 3.5)""") == Seq(Some("E0022")))
    }
    it("matches what the same overloads on a static method report") {
      val m =
        """class M {
          |public:
          |  static def two(f: Function[String, String], g: Function[String, String]): String = "FF"
          |  static def two(f: UnaryOperator[String], g: Function[String, String]): String = "UF"
          |  static def two(f: Function[String, String], g: UnaryOperator[String]): String = "FU"
          |}
          |""".stripMargin
      assert(errorCodes(imports + m + """IO::println(M::two((a) -> a + "1", (b) -> b + "2"))""") ==
        Seq(Some("E0006")))
    }
  }

  describe("a lambda constructor call that is not ambiguous") {
    it("still picks the one most specific constructor") {
      val src = imports + two.replace(
        "  def this(f: Function[String, String], g: UnaryOperator[String]) { this.tag = \"FU\" }\n",
        "  def this(f: UnaryOperator[String], g: UnaryOperator[String]) { this.tag = \"UU\" }\n") +
        """def main(args: String[]): String = new Two((a) -> a + "1", (b) -> b + "2").tag""" + "\n"
      assert(Shell.Success("UU") == shell.run(src, "None", Array()))
    }
    it("still picks the only constructor a plain argument allows") {
      val src = imports + mix + """def main(args: String[]): String = new Mix(new Object(), (a) -> a + "!").tag""" + "\n"
      assert(Shell.Success("OU") == shell.run(src, "None", Array()))
    }
  }
}
