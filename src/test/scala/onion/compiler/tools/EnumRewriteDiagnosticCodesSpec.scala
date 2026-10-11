package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Two rewrite-phase diagnostics (`AdtEnumLowering`/`Rewriting`) used to throw a
 * raw, uncoded, English-only `CompileError` instead of going through the
 * bilingual `SemanticError`/`Message`/`errorMessage*.properties` lookup every
 * other diagnostic uses -- no error code, and no Japanese translation under
 * `-Duser.language=ja`. They now carry E0098/E0099 and resolve through
 * `Message(...)`, the same lookup production code uses, so the expected text
 * here holds under both the `en` and `ja` full-suite runs instead of hardcoding
 * one language's text (see CLAUDE.md's locale-independence testing note).
 */
class EnumRewriteDiagnosticCodesSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("an ADT enum that also declares shared constructor parameters") {
    it("carries E0098 and names the enum") {
      val result = compile(
        """
          |enum Mixed(tag: String) {
          |  case A(x: Int)
          |  case B
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(errors.exists(_.code.contains("E0098")), s"expected E0098, got: ${errors.map(_.code)}")

      val expected = Message("error.semantic.adtEnumMixesSharedParams", Array[Any]("Mixed"))
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }

  describe("a homogeneous enum that declares type parameters") {
    it("carries E0099 and names the enum") {
      val result = compile(
        "enum Color[T] { RED, GREEN }\ndef main(args: String[]): void { }"
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(errors.exists(_.code.contains("E0099")), s"expected E0099, got: ${errors.map(_.code)}")

      val expected = Message("error.semantic.homogeneousEnumWithTypeParameters", Array[Any]("Color"))
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }
}
