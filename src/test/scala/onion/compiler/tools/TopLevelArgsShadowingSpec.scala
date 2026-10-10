package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A bare top-level script (no explicit `main`/`tool`) implicitly binds `args`
 * to the script's command-line arguments. Declaring a `val`/`var` named `args`
 * at top level used to collide with that invisible binding instead of simply
 * shadowing it: `val args = ...` misreported `[E0007] duplicated local
 * variable definition args` against a binding with no source location to
 * point at, and `var args = ...` compiled with no error but every read of
 * `args` kept resolving to the (empty, in these tests) CLI-args array instead
 * of the user's own value (#2039).
 */
class TopLevelArgsShadowingSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runAndCapture(src: String): String = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (o, e) = (System.out, System.err)
    try {
      System.setOut(ps); System.setErr(ps)
      Console.withOut(ps) { Console.withErr(ps) {
        shell.run(src, "None", Array())
      } }
    } finally { System.setOut(o); System.setErr(e) }
    new String(buf.toByteArray, "UTF-8")
  }

  it("does not report E0007 for a top-level `val args`") {
    val codes = errorCodes("val args: Int = 5\nIO::println(args)\n")
    assert(!codes.contains("E0007"), s"must not misreport E0007 for a user-declared args: $codes")
  }

  it("runs a top-level `val args` and prints the user's own value, not E0007") {
    val out = runAndCapture("val args: Int = 5\nIO::println(args)\n")
    assert(out.trim == "5", s"expected 5, got: $out")
  }

  it("runs a top-level `var args` and prints the user's own value, not the CLI-args array") {
    val out = runAndCapture("var args: Int = 5\nIO::println(args)\n")
    assert(out.trim == "5", s"expected 5, got: $out")
  }

  it("still lets a mutable top-level `args` be reassigned") {
    val out = runAndCapture("var args: Int = 1\nargs = args + 1\nIO::println(args)\n")
    assert(out.trim == "2", s"expected 2, got: $out")
  }

  it("still binds `args` to the real command-line arguments when not shadowed") {
    val src =
      """
        |IO::println(args.length)
        |foreach a: String in args {
        |  IO::println(a)
        |}
        |""".stripMargin
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (o, e) = (System.out, System.err)
    try {
      System.setOut(ps); System.setErr(ps)
      Console.withOut(ps) { Console.withErr(ps) {
        shell.run(src, "None", Array("foo", "bar"))
      } }
    } finally { System.setOut(o); System.setErr(e) }
    val out = new String(buf.toByteArray, "UTF-8")
    assert(out.trim == "2\nfoo\nbar", s"expected the real CLI args to come through, got: $out")
  }
}
