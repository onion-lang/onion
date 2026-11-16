package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader
import org.scalatest.funspec.AnyFunSpec

/**
 * Two scripts named `x.on` in different directories both become the class `xMain` (#2690).
 * A file with only declarations has no `xMain` of its own, so it must not pick up the
 * other file's (#2956).
 */
class SameNameScriptsSpec extends AnyFunSpec {
  private def compile(files: (String, String)*): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.code)
      case _ => Seq.empty
    }
  }

  private def compileDetailed(files: (String, String)*) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compileDetailed(inputs)
  }

  private val classOnly = "class A {\npublic:\n  def this {}\n}\n"

  describe("a class-only file and a script that share a file name") {
    for ((label, files) <- Seq(
      "class file first" -> Seq("e1/y.on" -> classOnly, "e2/y.on" -> "println(\"two\")"),
      "script first" -> Seq("e2/y.on" -> "println(\"two\")", "e1/y.on" -> classOnly))) {
      it(s"compiles with no internal error or warning ($label)") {
        val result = compileDetailed(files*)
        assert(!result.hasErrors, result.allErrors.map(e => e.code.getOrElse("") + " " + e.message))
        assert(result.diagnostics.warnings.isEmpty, result.diagnostics.warnings.map(_.message))
        assert(result.classes.map(_.className).toSet == Set("A", "yMain"))
      }
    }
  }

  describe("scripts that share a file name") {
    it("names both files' paths in the E0008") {
      val result = compileDetailed("d1/x.on" -> "println(\"one\")", "d2/x.on" -> "println(\"two\")")
      val messages = result.allErrors.map(_.message)
      assert(messages.exists(m => m.contains("d1/x.on") && m.contains("d2/x.on")), messages)
    }
    it("is a user-facing E0008, not an internal error") {
      val codes = compile("d1/x.on" -> "println(\"one\")", "d2/x.on" -> "println(\"two\")")
      assert(codes == Seq("E0008"), codes)
    }
    it("still compiles scripts with different names") {
      assert(compile("a.on" -> "println(\"one\")", "b.on" -> "println(\"two\")").isEmpty)
    }
  }
}
