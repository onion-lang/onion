package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `ScriptRunner.run` translated every successful `main` invocation to process exit
 * code 0, discarding the `Int` `main` actually returned. `Shell.run` already carries
 * that value correctly (`Shell.Success(value)` -- see `ToolContractCliSpec`, which
 * asserts on the wrapped value directly), but `ScriptRunner`, the real `onion` CLI
 * entry point, threw it away: `case Shell.Success(_) => 0`. `docs/guide/tools.md`
 * documents a failing tool CLI as returning `1` "from main"; nothing translated that
 * into the OS-level exit code the actual command line reports, so a missing argument
 * or unknown option printed the right message and still exited 0.
 */
class ScriptRunnerExitCodeSpec extends AnyFunSuite with Matchers:
  private def captureOut(body: => Int): (Int, String) =
    val buf = new ByteArrayOutputStream()
    val ps = new PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val exitCode =
      try
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps)(Console.withErr(ps)(body))
      finally { System.setOut(savedOut); System.setErr(savedErr) }
    (exitCode, new String(buf.toByteArray, StandardCharsets.UTF_8))

  private def writeScript(source: String): String =
    val src = Files.createTempFile("onion-exitcode", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)
    src.toString

  test("a main returning a non-zero Int becomes the process exit code"):
    val path = writeScript(
      """def main(args: String[]): Int {
        |  IO::println("about to fail")
        |  return 1
        |}
        |""".stripMargin)

    val (exitCode, out) = captureOut(new ScriptRunner().run(Array(path)))

    exitCode shouldBe 1
    out should include("about to fail")

  test("a main returning 0 keeps exiting 0"):
    val path = writeScript(
      """def main(args: String[]): Int {
        |  return 0
        |}
        |""".stripMargin)

    captureOut(new ScriptRunner().run(Array(path)))._1 shouldBe 0

  test("a void main still exits 0"):
    val path = writeScript(
      """def main(): void {
        |  IO::println("ok")
        |}
        |""".stripMargin)

    captureOut(new ScriptRunner().run(Array(path)))._1 shouldBe 0

  test("a tool CLI's missing-argument failure exits 1, not 0 (docs/guide/tools.md)"):
    val path = writeScript(
      """tool greet(name: String): Int requires { console } {
        |  IO::println("hello " + name)
        |  return 0
        |}
        |""".stripMargin)

    val (exitCode, out) = captureOut(new ScriptRunner().run(Array(path)))

    exitCode shouldBe 1
    out should include("missing argument")

  test("a tool CLI's unknown-option failure exits 1"):
    val path = writeScript(
      """tool greet(name: String): Int requires { console } {
        |  IO::println("hello " + name)
        |  return 0
        |}
        |""".stripMargin)

    val (exitCode, _) = captureOut(new ScriptRunner().run(Array(path, "world", "--badflag")))

    exitCode shouldBe 1

  test("a tool CLI's successful run still exits 0"):
    val path = writeScript(
      """tool greet(name: String): Int requires { console } {
        |  IO::println("hello " + name)
        |  return 0
        |}
        |""".stripMargin)

    val (exitCode, out) = captureOut(new ScriptRunner().run(Array(path, "world")))

    exitCode shouldBe 0
    out should include("hello world")
