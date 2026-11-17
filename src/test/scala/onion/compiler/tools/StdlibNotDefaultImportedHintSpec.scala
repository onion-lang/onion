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

  describe("#2925: the hint only names a class whose overload takes the arguments") {
    it("does not point bytes(String) at Format::bytes(Long)") {
      val (codes, msgs) = messagesOf("def main(): void {\n  val d = bytes(\"abc\")\n}\n")
      assert(codes.contains("E0005"), codes.toString)
      assert(!msgs.contains("Format::bytes"), msgs)
    }
    it("does not point sha256(Int) at Hash::sha256(String)") {
      val (codes, msgs) = messagesOf("def main(): void {\n  val h = sha256(42)\n}\n")
      assert(codes.contains("E0005"), codes.toString)
      assert(!msgs.contains("Hash::sha256"), msgs)
    }
    it("still hints at Format::bytes for a Long") {
      val (_, msgs) = messagesOf("def main(): void {\n  IO::println(bytes(1024L))\n}\n")
      assert(msgs.contains("Format::bytes"), msgs)
    }
    it("still hints at Stats::average for a List[Int]") {
      val (_, msgs) = messagesOf("def main(): void {\n  IO::println(average([1, 2, 3]))\n}\n")
      assert(msgs.contains("Stats::average"), msgs)
    }
  }
}
