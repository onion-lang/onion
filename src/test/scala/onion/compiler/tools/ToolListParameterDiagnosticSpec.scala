package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Issue #2254 (RFC, Option 3): a `tool` cannot take a list of arguments yet. A
 * `List[String]` or `String[]` parameter is still E0081, but the message now says
 * so and names the workaround (a flag split with `Strings::split`) instead of only
 * listing the scalar types. Expected text goes through `Message(...)`, so the spec
 * holds in both the `en` and `ja` runs.
 */
class ToolListParameterDiagnosticSpec extends AnyFunSpec {

  private def compile(code: String) =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private def assertListRejected(tpe: String): Unit = {
    val result = compile(
      s"""
         |tool greet(names: $tpe) requires { console } {
         |  IO::println("hi")
         |}
         |""".stripMargin)
    assert(result.hasErrors, "expected E0081, but it compiled")
    val hit = result.allErrors.find(_.code.contains("E0081"))
    assert(hit.isDefined, s"expected E0081, got: ${result.allErrors.map(e => (e.code, e.message))}")
    val expected = Message("error.semantic.toolListParameter", Array[Any]("greet", "names", tpe))
    assert(hit.get.message.contains(expected), s"expected '$expected', got: ${hit.get.message}")
    assert(!hit.get.message.contains("String[]") || tpe == "String[]")
  }

  describe("a list-typed parameter on a tool") {
    it("List[String] is E0081 with the list-specific message") { assertListRejected("List[String]") }
    it("String[] is E0081 with the list-specific message") { assertListRejected("String[]") }
  }
}
