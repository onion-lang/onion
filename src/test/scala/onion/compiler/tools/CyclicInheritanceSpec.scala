package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0016 (CYCLIC_INHERITANCE) is reported for both classes and interfaces
 * whose supertype chain loops back on itself, but had no regression
 * coverage at all before this spec.
 */
class CyclicInheritanceSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("cyclic inheritance") {
    it("reports E0016 for a direct class self-cycle") {
      val results = errors(
        """
          |class A extends A {
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0016")))
    }

    it("reports E0016 for an indirect class cycle") {
      val results = errors(
        """
          |class A extends B {
          |}
          |class B extends A {
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0016")))
    }

    it("reports E0016 for an interface cycle") {
      val results = errors(
        """
          |interface I conforms J {
          |}
          |interface J conforms I {
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0016")))
    }

    it("does not report E0016 for a normal, non-cyclic inheritance chain") {
      val results = errors(
        """
          |class A {
          |}
          |class B extends A {
          |}
          |class C extends B {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0016")))
    }
  }
}
