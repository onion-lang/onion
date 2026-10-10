package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A top-level script `var` is never narrowed by a null check (#2026), unlike a
 * top-level `val` or a method-local `var` -- so `if s != null { s.length }` still
 * reports E0070 even though the check the message itself recommends was already
 * written. RFC #2031 is still open on whether to narrow it; until it resolves,
 * this adds "option 1" from that RFC: a hint specific to this case (copy the
 * value to a local first) instead of only repeating the generic "check for null
 * first" advice the user already followed. No semantics change: still E0070.
 */
class TopLevelVarNullableMemberAccessSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")}: ${e.message}")
      case _ => Seq.empty
    }
  }

  it("hints to copy a top-level var to a local, in addition to reporting E0070") {
    val msgs = messages(
      """
        |var s: String? = "hi"
        |if s != null {
        |  IO::println(s.length)
        |}
        |""".stripMargin
    ).mkString("\n")
    assert(msgs.contains("E0070"), s"expected E0070, got: $msgs")
    assert(msgs.contains("val t = s"), s"expected the copy-to-a-local hint, got: $msgs")
  }

  it("does not add the top-level-var hint for an ordinary nullable member access") {
    val msgs = messages(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val x: String? = "abc"
        |    return x.length
        |  }
        |}
        |""".stripMargin
    ).mkString("\n")
    assert(msgs.contains("E0070"), s"expected E0070, got: $msgs")
    assert(!msgs.contains("val t = s"), s"the top-level-var hint should not fire here: $msgs")
  }

  it("does not add the top-level-var hint for a method-local var") {
    val msgs = messages(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    var x: String? = "abc"
        |    x = null
        |    return x.length
        |  }
        |}
        |""".stripMargin
    ).mkString("\n")
    assert(msgs.contains("E0070"), s"expected E0070, got: $msgs")
    assert(!msgs.contains("val t = s"), s"the top-level-var hint should not fire here: $msgs")
  }
}
