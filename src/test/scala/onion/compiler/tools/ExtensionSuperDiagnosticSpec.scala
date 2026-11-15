package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import java.io.StringReader

/**
 * `super` in an `extension` method used to report E0050 "static context", although `this`
 * works there (#2688). The error stays, but now names the real reason.
 */
class ExtensionSuperDiagnosticSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src.stripMargin), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
      case _ => Seq.empty
    }
  }

  describe("super inside an extension method (#2688)") {
    it("is still E0050, and the message points at the extension, not at a static context") {
      val errs = errors(
        """
          |extension String {
          |  def tw(): String = super.toString() + this
          |}
          |IO::println("ab".tw())
        """)
      assert(errs.size == 1)
      assert(errs.head.message.contains("`super`"))
      assert(errs.head.message.contains("`extension`"))
    }

    it("keeps the plain E0050 text for super in a static method") {
      val errs = errors(
        """
          |class A {
          |public:
          |  static def f(): String = super.toString()
          |}
        """)
      assert(errs.size == 1)
      assert(!errs.head.message.contains("`extension`"))
    }
  }
}
