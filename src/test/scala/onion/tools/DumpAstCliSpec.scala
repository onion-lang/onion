package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `--dump-ast` / `--dump-typed-ast` (documented in CLAUDE.md and both CLIs' usage text):
 * print the parsed/typed AST to stderr. Exercised end to end through the real CLI entry
 * points — `CompilerFrontend`/`ScriptRunner` parsed the flags into `CompilerConfig` but
 * never read them back, so both flags were silently no-ops.
 */
class DumpAstCliSpec extends AnyFunSuite with Matchers:
  private def captureErr(body: => Int): (Int, String) =
    val buf = new ByteArrayOutputStream()
    val ps = new PrintStream(buf, true, "UTF-8")
    val saved = System.err
    val exitCode =
      try
        System.setErr(ps)
        Console.withErr(ps)(body)
      finally System.setErr(saved)
    (exitCode, new String(buf.toByteArray, StandardCharsets.UTF_8))

  private val source =
    """class App {
      |public:
      |  static def f(p: String): String = p
      |}
      |""".stripMargin

  test("CompilerFrontend --dump-ast prints the parsed AST to stderr"):
    val output = Files.createTempDirectory("onion-dump-ast-cli")
    val src = Files.createTempFile("onion-dump-ast-cli", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, "--dump-ast", src.toString))

    exitCode shouldBe 0
    err should include("[AST]")

  test("CompilerFrontend --dump-typed-ast prints the typed AST to stderr"):
    val output = Files.createTempDirectory("onion-dump-ast-cli")
    val src = Files.createTempFile("onion-dump-ast-cli", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, "--dump-typed-ast", src.toString))

    exitCode shouldBe 0
    err should include("[Typed AST]")
    err should include("App")

  test("CompilerFrontend without --dump-ast/--dump-typed-ast prints neither"):
    val output = Files.createTempDirectory("onion-dump-ast-cli")
    val src = Files.createTempFile("onion-dump-ast-cli", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, src.toString))

    exitCode shouldBe 0
    err should not include "[AST]"
    err should not include "[Typed AST]"

  test("CompilerFrontend --dump-ast still prints the parsed AST when typing later fails"):
    val output = Files.createTempDirectory("onion-dump-ast-cli")
    val src = Files.createTempFile("onion-dump-ast-cli-bad", ".on")
    Files.writeString(src,
      """class App {
        |public:
        |  static def f(): Int = "not an int"
        |}
        |""".stripMargin, StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, "--dump-ast", src.toString))

    exitCode shouldBe -1
    err should include("[AST]")

  test("ScriptRunner --dump-ast prints the parsed AST to stderr"):
    val src = Files.createTempFile("onion-dump-ast-cli-script", ".on")
    Files.writeString(src, source + "\ndef main: void {}\n", StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new ScriptRunner().run(Array("--dump-ast", src.toString))

    exitCode shouldBe 0
    err should include("[AST]")

  test("ScriptRunner --dump-typed-ast prints the typed AST to stderr"):
    val src = Files.createTempFile("onion-dump-ast-cli-script", ".on")
    Files.writeString(src, source + "\ndef main: void {}\n", StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new ScriptRunner().run(Array("--dump-typed-ast", src.toString))

    exitCode shouldBe 0
    err should include("[Typed AST]")
    err should include("App")
