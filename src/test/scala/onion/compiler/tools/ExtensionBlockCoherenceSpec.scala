package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Two `extension` blocks for the same receiver type within one compilation unit used to
 * collide as a generic duplicate-class error (E0008), naming the internal mangled container
 * class (`Extension$List_int`) rather than the type the user wrote, and leaving every method
 * the second block declared silently unreachable -- reported, if at all, through an unrelated
 * "method not found" at the call site (issue #2204). This asserts the dedicated E0102
 * diagnostic instead, mirroring TypeClassCoherenceSpec's treatment of a duplicate `instance`.
 */
class ExtensionBlockCoherenceSpec extends AbstractShellSpec {
  private def errorMessages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message)
      case _ => Seq.empty
    }
  }

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.code)
      case _ => Seq.empty
    }
  }

  describe("extension block coherence") {
    it("rejects two extension blocks for the same concrete instantiation (repro 1 of #2204)") {
      assert(Shell.Failure(-1) == shell.run(
        "extension List[Int] { def foo(): Int = 1 }\nextension List[Int] { def bar(): Int = 2 }\ndef main(): void { val xs: List[Int] = [1,2,3]; IO::println(xs.bar()) }",
        "None", Array()))
    }
    it("reports the duplicate-extension-block message, bilingual, carrying E0102 (#2204)") {
      // The receiver type renders the way the AST sees it pre-resolution -- a written
      // `Int` is the primitive descriptor's own JVM-style name ("int"), matching the
      // same pre-typing rendering used elsewhere (e.g. ExplicitTypeArgPrimitiveSpec).
      val expected = onion.compiler.toolbox.Message(
        "error.semantic.duplicateExtensionBlock", Array[Any]("List[int]"))
      val messages = errorMessages(
        "extension List[Int] { def foo(): Int = 1 }\nextension List[Int] { def bar(): Int = 2 }\ndef main(): void { val xs: List[Int] = [1,2,3]; IO::println(xs.bar()) }")
      assert(messages.exists(_.contains(expected)), s"expected a message containing '$expected', got: $messages")
    }
    it("rejects the same non-generic type split across two blocks (repro 2 of #2204)") {
      assert(Shell.Failure(-1) == shell.run(
        "extension List { def tagA(): String = \"A\" }\nextension List { def tagB(): String = \"B\" }\ndef main(): void { val xs: List[Int] = [1,2,3]; IO::println(xs.tagB()) }",
        "None", Array()))
    }
    it("names the exact code E0102, not the generic duplicate-class E0008") {
      val codes = errorCodes(
        "extension List { def tagA(): String = \"A\" }\nextension List { def tagB(): String = \"B\" }\ndef main(): void { val xs: List[Int] = [1,2,3]; IO::println(xs.tagB()) }")
      assert(codes.contains("E0102"), s"expected E0102 in diagnostics, got: $codes")
      assert(!codes.contains("E0008"), s"E0008 should no longer leak for this case, got: $codes")
    }
    it("allows distinct concrete instantiations of the same generic receiver to coexist") {
      assert(Shell.Success("3") == shell.run(
        "extension List[Int] { def tagInt(): String = \"i\" }\nextension List[String] { def tagStr(): String = \"s\" }\ndef main(args: String[]): String { val xs: List[Int] = [1,2,3]; return \"\" + xs.size }",
        "None", Array()))
    }
    it("allows a single extension block to coexist peacefully (no false positive)") {
      assert(Shell.Success("OK") == shell.run(
        "extension String { def shout(): String = self.toUpperCase() }\ndef main(args: String[]): String { return \"ok\".shout() }",
        "None", Array()))
    }
  }
}
