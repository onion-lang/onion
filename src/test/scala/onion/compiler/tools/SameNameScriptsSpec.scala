package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader
import org.scalatest.funspec.AnyFunSpec

/** Two scripts named `x.on` in different directories both become the class `xMain` (#2690). */
class SameNameScriptsSpec extends AnyFunSpec {
  private def compile(files: (String, String)*): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.code)
      case _ => Seq.empty
    }
  }

  describe("scripts that share a file name") {
    it("is a user-facing E0008, not an internal error") {
      val codes = compile("d1/x.on" -> "println(\"one\")", "d2/x.on" -> "println(\"two\")")
      assert(codes == Seq("E0008"), codes)
    }
    it("still compiles scripts with different names") {
      assert(compile("a.on" -> "println(\"one\")", "b.on" -> "println(\"two\")").isEmpty)
    }
  }
}
