package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A class member method declared `static` with no body (no `{ ... }`, no
 * `= expr`) went completely unchecked by TypingOutlinePass: the grammar
 * accepts a bodyless method as an abstract/interface-style declaration
 * (`method_decl`'s `eos_or_block_end()` alternative), and
 * `processMethodDeclaration` unconditionally OR'd in `M_ABSTRACT` whenever
 * `node.block == null` — even when the method already carried `M_STATIC`.
 * The resulting class file declared a method with both ACC_STATIC and
 * ACC_ABSTRACT, which is illegal per the JVM spec; loading it (e.g. via
 * `OnionClassLoader`, exercised by `LawCheckPhase`) threw a raw
 * `java.lang.ClassFormatError: Method ... has illegal modifiers: 0x409`
 * that surfaced as an uncaught internal compiler error (I0000) instead of
 * a normal diagnostic. Caught by the mutation fuzzer stripping the opening
 * brace from `main`'s body in `run/LineFilter.on`.
 */
class StaticMethodWithoutBodySpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("a static method with no body") {
    it("reports E0085 instead of generating an illegal ACC_STATIC|ACC_ABSTRACT method") {
      val results = errors(
        """
          |class LineFilter {
          |public:
          |  static def main(args: String[]): void
          |  static def helper(): Int { return 1 }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0085")))
      val message = results.collectFirst { case (Some("E0085"), msg) => msg }.get
      assert(message.contains("main"))
    }

    it("does not affect an ordinary abstract instance method in an abstract class") {
      val results = errors(
        """
          |abstract class Base {
          |public:
          |  abstract def compute(): Int
          |}
          |class Impl extends Base {
          |public:
          |  override def compute(): Int { return 1 }
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0085")))
    }
  }
}
