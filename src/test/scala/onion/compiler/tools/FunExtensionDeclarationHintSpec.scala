package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Kotlin-style extension-function declaration, `fun String.twice(): String { ... }`,
 * is a more specific mistake than the bare `fun name(...)` case ([[FunDeclarationHintSpec]]):
 * the receiver-dot syntax means the generic `fun`/`func`/`fn` hint's `<kw> name(` pattern
 * never matches (the identifier right after the keyword is the receiver type, followed by
 * `.`, not `(`), so without its own case this falls through to a generic parse error
 * instead of naming Onion's `extension` block.
 */
class FunExtensionDeclarationHintSpec extends AbstractShellSpec {
  private def messages(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("fun Type.method(...) extension declaration") {
    it("hints at an extension block for a top-level Kotlin-style receiver method") {
      val msgs = messages("fun String.twice(): String { return this + this }\n")
      assert(msgs.contains("extension"), s"expected a hint mentioning extension, got: $msgs")
      assert(msgs.contains("String"), s"expected the hint to name the receiver type, got: $msgs")
      assert(msgs.contains("twice"), s"expected the hint to name the method, got: $msgs")
    }

    it("hints at an extension block for a func-spelled receiver method") {
      val msgs = messages("func Int.doubled(): Int { return this * 2 }\n")
      assert(msgs.contains("extension"), s"expected a hint mentioning extension, got: $msgs")
      assert(msgs.contains("doubled"), s"expected the hint to name the method, got: $msgs")
    }

    it("hints at an extension block for a function-spelled receiver method") {
      val msgs = messages("function Int.doubled(): Int { return this * 2 }\n")
      assert(msgs.contains("extension"), s"expected a hint mentioning extension, got: $msgs")
      assert(msgs.contains("doubled"), s"expected the hint to name the method, got: $msgs")
    }

    it("still hints at def (not extension) for a receiver-less fun declaration") {
      val msgs = messages("fun greet(): void { IO::println(\"hi\") }\n")
      assert(msgs.contains("def"), s"expected a hint mentioning def, got: $msgs")
      assert(!msgs.contains("extension"), s"receiver-less fun should not mention extension, got: $msgs")
    }

    it("does not fire for a call to a method literally named fun") {
      val msgs = messages("val x = fun(1, 2)\n")
      assert(!msgs.contains("extension"), s"hint should not fire for a fun(...) call, got: $msgs")
    }
  }
}
