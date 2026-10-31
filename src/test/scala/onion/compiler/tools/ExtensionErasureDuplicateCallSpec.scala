package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

class ExtensionErasureDuplicateCallSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("an extension method whose erasure duplicates another in its block") {
    it("is one E0084 at the declaration, with no extra error at the call (#2475)") {
      val result = codes(
        """extension String {
          |  def p(f: (Int) -> Int): String = "i"
          |  def p(f: (String) -> String): String = "s"
          |}
          |IO::println("a".p((x: Int) -> x))
          |""".stripMargin
      )
      assert(result == Seq(Some("E0084")))
    }

    it("still resolves calls when the signatures differ after erasure") {
      val result = codes(
        """extension String {
          |  def p(f: (Int) -> Int): String = "i"
          |  def p(n: Int): String = "n"
          |}
          |IO::println("a".p((x: Int) -> x))
          |IO::println("a".p(1))
          |""".stripMargin
      )
      assert(result.isEmpty)
    }
  }
}
