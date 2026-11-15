package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** #2723: a bare `sum(xs)` / `sha256(s)` names the qualified `Stats::sum` / `Hash::sha256`. */
class StdlibNotDefaultImportedHintSpec extends AbstractShellSpec {
  private def messagesOf(src: String): (Seq[String], String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => (errs.flatMap(_.code), errs.map(_.message).mkString("\n"))
      case _ => (Seq.empty, "")
    }
  }

  describe("bare call to a pure stdlib helper that is not default-imported") {
    it("hints at Stats::sum") {
      val (codes, msgs) = messagesOf("def main(): void {\n  IO::println(sum([1, 2, 3]))\n}\n")
      assert(codes.contains("E0005"), codes.toString)
      assert(msgs.contains("Stats::sum"), msgs)
    }
    it("hints at Hash::sha256") {
      val (codes, msgs) = messagesOf("def main(): void {\n  IO::println(sha256(\"abc\"))\n}\n")
      assert(codes.contains("E0005"), codes.toString)
      assert(msgs.contains("Hash::sha256"), msgs)
    }
  }
}
