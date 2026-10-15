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

  // RFC #2031 option 2: a top-level `var` that no def, class or lambda assigns is only written by
  // the script's own straight-line code, so a null check narrows it flow-sensitively like a local.
  private def e0070(src: String) = messages(src).exists(_.startsWith("E0070"))

  it("narrows a top-level var after a null check when only straight-line code assigns it") {
    assert(!e0070(
      """
        |var s: String? = "hi"
        |if s != null {
        |  IO::println(s.length)
        |}
        |""".stripMargin))
  }

  it("narrows a top-level var through && short-circuit") {
    assert(!e0070(
      """
        |var s: String? = "hi"
        |if s != null && s.length > 0 { IO::println(s.length) }
        |""".stripMargin))
  }

  it("narrows a top-level var in the else branch of == null") {
    assert(!e0070(
      """
        |var s: String? = "hi"
        |if s == null { IO::println("none") } else { IO::println(s.length) }
        |""".stripMargin))
  }

  it("does not leak the narrowing onto a same-named field of a class") {
    assert(e0070(
      """
        |var s: String? = "hi"
        |class Box {
        |  var s: String?
        |public:
        |  def this { s = null }
        |  def len(): Int {
        |    if s != null { return s.length }
        |    return 0
        |  }
        |}
        |""".stripMargin))
  }

  it("runs: the narrowed read sees the current value") {
    val result = shell.run(
      """
        |var s: String? = "hello"
        |var out: Int = -1
        |if s != null { out = s.length }
        |s = null
        |IO::println(out)
        |""".stripMargin, "None", Array())
    assert(result.isInstanceOf[onion.tools.Shell.Success])
  }

  it("does not narrow outside the guarded branch") {
    assert(e0070(
      """
        |var s: String? = "hi"
        |if s != null { IO::println(s.length) }
        |IO::println(s.length)
        |""".stripMargin))
  }

  it("does not narrow when the branch reassigns the var before the use") {
    assert(e0070(
      """
        |var s: String? = "hi"
        |if s != null {
        |  s = null
        |  IO::println(s.length)
        |}
        |""".stripMargin))
  }

  it("does not narrow when a top-level def assigns the var") {
    assert(e0070(
      """
        |var s: String? = "hi"
        |def clear(): void { s = null }
        |if s != null {
        |  clear()
        |  IO::println(s.length)
        |}
        |""".stripMargin))
  }

  it("does not narrow when a lambda assigns the var") {
    assert(e0070(
      """
        |var s: String? = "hi"
        |val clear: () -> void = () -> { s = null }
        |if s != null {
        |  clear()
        |  IO::println(s.length)
        |}
        |""".stripMargin))
  }

  it("does not narrow when a class method assigns the var") {
    assert(e0070(
      """
        |var s: String? = "hi"
        |class Clearer {
        |public:
        |  def clear(): void { s = null }
        |}
        |if s != null {
        |  new Clearer().clear()
        |  IO::println(s.length)
        |}
        |""".stripMargin))
  }

  it("keeps the top-level-var hint where the var is still not narrowable") {
    val msgs = messages(
      """
        |var s: String? = "hi"
        |def clear(): void { s = null }
        |if s != null {
        |  clear()
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
