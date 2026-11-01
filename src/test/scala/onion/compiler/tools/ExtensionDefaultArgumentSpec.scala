package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A default on an extension method parameter (#2401), and the review panel's blocks on #2402:
 * an overload that takes exactly the written arguments keeps winning over one that needs a
 * default, and a default cannot see `self` or the other parameters (it is spliced into the
 * call site, where they do not exist).
 */
class ExtensionDefaultArgumentSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def main(decls: String, body: String): String =
    s"""
       |$decls
       |class Main {
       |public:
       |  static def main(args: String[]): String {
       |    return $body
       |  }
       |}
       |""".stripMargin

  private def run(decls: String, body: String) = shell.run(main(decls, body), "None", Array())

  describe("Extension method default arguments") {
    it("an exact-arity overload beats one that needs a default (repro 1)") {
      val decls =
        """extension String {
          |  def h(a: Int): String = "one" + a
          |  def h(a: Int, b: Int = 5): String = "two" + a + b
          |}""".stripMargin
      assert(run(decls, "\"s\".h(3) + \",\" + \"s\".h(3, 4)") == Shell.Success("one3,two34"))
    }

    it("a zero-argument overload beats one whose only parameter has a default") {
      val decls =
        """extension String {
          |  def r: String = "zero"
          |  def r(a: Int = 4): String = "one" + a
          |}""".stripMargin
      assert(run(decls, "\"s\".r() + \",\" + \"s\".r(7)") == Shell.Success("zero,one7"))
    }

    it("a default that reads self is E0050, not the caller's slot (repro 2)") {
      val src = main("""extension String { def g(c: String = self + "!"): String = self + "/" + c }""", "\"s\".g()")
      assert(errorCodes(src) == Seq("E0050"))
    }

    it("a default that reads an earlier parameter is E0002, not a VerifyError") {
      val src = main("""extension String { def g(a: Int, b: Int = a * 10): String = self + a + b }""", "\"s\".g(1)")
      assert(errorCodes(src) == Seq("E0002"))
    }

    it("fills the defaults of the overload chosen by parameter types (repro 3)") {
      val decls =
        """extension String {
          |  def f(a: Int, b: Int = 1): String = "int" + a + b
          |  def f(a: String, b: Int = 2): String = "str" + a + b
          |}""".stripMargin
      assert(run(decls, "\"s\".f(\"y\") + \",\" + \"s\".f(7)") == Shell.Success("stry2,int71"))
    }

    it("an exact-arity extension on a wider receiver still wins (repro 4)") {
      val decls =
        """extension Object { def g(a: Int): String = "Object.g:" + a }
          |extension String { def g(a: Int, b: Int = 0): String = "String.g:" + a + "," + b }""".stripMargin
      assert(run(decls, "\"s\".g(1) + \";\" + \"s\".g(1, 2)") == Shell.Success("Object.g:1;String.g:1,2"))
    }

    it("a builtin exact-arity extension still wins over a user one with a default (repro 4)") {
      val decls = """extension List { def take(n: Int, tag: String = "user"): String = tag + ":" + n }"""
      assert(run(decls, "\"\" + [1, 2, 3].take(2) + \";\" + [1, 2, 3].take(2, \"t\")") == Shell.Success("[1, 2];t:2"))
    }

    it("a default that names a constant still works") {
      val decls = """extension String { def pad(w: Int = 3 * 2): String = self + "|" + w }"""
      assert(run(decls, "\"ab\".pad() + \",\" + \"ab\".pad(1)") == Shell.Success("ab|6,ab|1"))
    }
  }
}
