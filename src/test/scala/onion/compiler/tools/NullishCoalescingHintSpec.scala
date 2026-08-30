package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * Onion has no JS/TS/Kotlin-style `??` nullish-coalescing operator. Without a
 * dedicated hint, a lone `?` reuses the ternary-operator hint (`if`/`else`),
 * which is the wrong fix here -- the actual replacement is the Elvis
 * operator `?:`.
 */
class NullishCoalescingHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("nullish-coalescing operator") {
    it("hints that Onion has no ?? operator, not the ternary hint") {
      val msgs = messages(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Int? = null
          |    val b: Int = a ?? 5
          |    return b
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("?:"), s"expected a hint mentioning the Elvis operator, got: $msgs")
      assert(!msgs.contains("if cond"), s"expected the ternary hint not to fire instead, got: $msgs")
    }
  }
}
