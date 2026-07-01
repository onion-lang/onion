package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Diagnostics render types in Onion source form (List[String], Map[String, Int])
 * rather than the JVM form (java.util.List[java.lang.String]), consistent with
 * the REPL.
 */
class DiagnosticTypeFormatSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("diagnostic type formatting") {
    it("renders generic types in source form, not JVM FQCN/boxed") {
      val msgs = messages("def size(xs: List[String]): Int = xs.size()\nIO::println(size([1]))\n").mkString("\n")
      assert(msgs.contains("List[Int]") && msgs.contains("List[String]"))
      assert(!msgs.contains("java.util.List"))
      assert(!msgs.contains("java.lang.Integer"))
    }
  }
}
