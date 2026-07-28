package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Both CLI entry points reject a malformed command line before touching any source file, but
 * they did so inconsistently:
 *
 *   - `CompilerFrontend` looked up the message key `error.command..noArgument` (a stray extra
 *     dot) for an option missing its required argument (e.g. `onionc -classpath` with nothing
 *     after it). The key does not exist, so `Message.apply` threw an uncaught
 *     `MissingResourceException` instead of printing a diagnostic — a CLI-level crash reachable
 *     from a plain typo, not just malformed source.
 *   - `ScriptRunner`'s `printFailure` only walked `failure.lackedOptions`, never
 *     `failure.invalidOptions`, so an unrecognized flag (e.g. `onion -maxErrorReports 5 x.on`,
 *     the exact typo `-maxErrorReports` vs. the real `-maxErrorReport` that used to be
 *     documented) failed with no message at all: exit code -1 and empty stderr.
 */
class CliInvalidOptionSpec extends AnyFunSuite with Matchers:
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

  test("CompilerFrontend reports a value-option missing its argument instead of crashing"):
    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-classpath"))

    exitCode shouldBe -1
    err should include("-classpath")
    err should not include "MissingResourceException"
    err should not include "ValuedParam"

  test("CompilerFrontend reports an unrecognized option"):
    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-maxErrorReports", "5", "Hello.on"))

    exitCode shouldBe -1
    err should include("-maxErrorReports")
    err should not include "ValuedParam"

  test("ScriptRunner reports an unrecognized option instead of failing silently"):
    val (exitCode, err) = captureErr:
      new ScriptRunner().run(Array("-maxErrorReports", "5", "Hello.on"))

    exitCode shouldBe -1
    err should include("-maxErrorReports")
    err should not include "ValuedParam"
