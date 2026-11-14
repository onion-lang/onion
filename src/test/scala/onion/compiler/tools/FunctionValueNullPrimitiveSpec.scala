package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** `null` never fills a primitive parameter of a function value (#2872): calling an
  * `(Int) -> T` with `null` is E0000, as for a method, not a run-time null-value crash. */
class FunctionValueNullPrimitiveSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("null passed to a primitive parameter of a function value") {
    it("is E0000 for f(null)") {
      assert(errorCodes("val f: (Int) -> String = (i) -> \"v\"\nprintln(f(null))\n") == Seq("E0000"))
    }
    it("is E0000 for f.call(null)") {
      assert(errorCodes("val f: (Int) -> String = (i) -> \"v\"\nprintln(f.call(null))\n") == Seq("E0000"))
    }
    it("is E0000 for the null among several arguments") {
      assert(errorCodes("val g: (String, Long) -> String = (s, n) -> s\nprintln(g(\"a\", null))\n") == Seq("E0000"))
    }
    it("is E0000 through a field holding the function") {
      val src =
        """
          |class K {
          |  val f: (Double) -> String
          |public:
          |  def this { f = (d) -> "d" }
          |  def go: String = f(null)
          |}
          |println(new K().go)
        """.stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("is E0000 for a function-typed parameter") {
      assert(errorCodes("def run(h: (Boolean) -> String): String = h(null)\nprintln(run((b) -> \"x\"))\n") == Seq("E0000"))
    }
    it("is a compile error when run") {
      assert(Shell.Failure(-1) == shell.run("val f: (Int) -> String = (i) -> \"v\"\nprintln(f(null))\n", "None", Array()))
    }
  }

  describe("what still compiles") {
    it("null for a nullable primitive parameter") {
      val src =
        """
          |def main(args: String[]): Int {
          |  val f: (Int?) -> String = (i) -> "v" + i
          |  println(f(null))
          |  return 0
          |}
        """.stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
    it("an Int argument") {
      val src =
        """
          |def main(args: String[]): Int {
          |  val f: (Int) -> Int = (i) -> i + 1
          |  return f(1) - 2
          |}
        """.stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
    it("null for a String parameter, with W0012 only") {
      val src =
        """
          |def main(args: String[]): Int {
          |  val f: (String) -> String = (s) -> "v"
          |  println(f(null))
          |  return 0
          |}
        """.stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
  }
}
