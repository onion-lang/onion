package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** A component named like a `derive!` method hides that method behind its accessor (#2758). */
class DeriveNamedLikeComponentSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a record component named like a derived method") {
    it("is E0026 for toJson with derive!(Json)") {
      assert(codes("record P(toJson: Int, n: Int) derive!(Json)\nprintln(new P(5, 2).toJson())\n") == Seq(Some("E0026")))
    }
    it("is E0026 for toYaml with derive!(Yaml)") {
      assert(codes("record P(toYaml: Int, n: Int) derive!(Yaml)\n") == Seq(Some("E0026")))
    }
    it("is E0026 for toMap with derive!(Json)") {
      assert(codes("record P(toMap: Int, n: Int) derive!(Json)\n") == Seq(Some("E0026")))
    }
    it("still compiles an ordinary derive!(Json) record") {
      assert(codes("record P(a: Int, n: Int) derive!(Json)\nprintln(P::toJson(new P(5, 2)))\n").isEmpty)
    }
  }
}
