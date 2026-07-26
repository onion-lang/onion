package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0007 (DUPLICATE_LOCAL_VARIABLE) is reported when a name is bound twice in
 * the same scope (a redeclared local, or a destructuring pattern that repeats
 * a binding name), but had no regression coverage at all before this spec.
 */
class DuplicateLocalVariableSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("a name bound twice in the same scope") {
    it("reports E0007 when a local variable is redeclared in the same block") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 1;
          |    val x: Int = 2;
          |    return x;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0007")))
    }

    it("reports E0007 when a destructuring declaration repeats a binding name") {
      val results = errors(
        """
          |record Pair(a: Int, b: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var (a, a) = new Pair(1, 2);
          |    return a;
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0007")))
    }

    it("does not report E0007 when an inner block shadows an outer local") {
      val results = errors(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 1;
          |    if x == 1 {
          |      val x: Int = 2;
          |      IO::println(x);
          |    }
          |    return x;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0007")))
    }
  }
}
