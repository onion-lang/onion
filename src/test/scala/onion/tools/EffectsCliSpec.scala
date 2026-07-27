package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `--effects` (documented in docs/reference/effects.md): prints each compiled method's
 * inferred effect set to stderr. Exercised end to end through the real CLI entry points
 * — `EffectInferenceSpec` covers the inference logic itself, this covers the flag wiring
 * in `CompilerFrontend`/`ScriptRunner` (`emitEffects`, gated on `!result.hasErrors`).
 */
class EffectsCliSpec extends AnyFunSuite with Matchers:
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
      |  static def f(p: String): String = Files::readText(p)
      |}
      |""".stripMargin

  test("CompilerFrontend --effects prints the inferred effect set to stderr"):
    val output = Files.createTempDirectory("onion-effects-cli")
    val src = Files.createTempFile("onion-effects-cli", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, "--effects", src.toString))

    exitCode shouldBe 0
    err should include("App#f")
    err should include("read")

  test("CompilerFrontend without --effects prints nothing about effects"):
    val output = Files.createTempDirectory("onion-effects-cli")
    val src = Files.createTempFile("onion-effects-cli", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, src.toString))

    exitCode shouldBe 0
    err should not include "App#f"

  test("CompilerFrontend --effects is silent when compilation fails"):
    val output = Files.createTempDirectory("onion-effects-cli")
    val src = Files.createTempFile("onion-effects-cli-bad", ".on")
    Files.writeString(src, "class App { public: static def f(: String\n", StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new CompilerFrontend().run(Array("-d", output.toString, "--effects", src.toString))

    exitCode shouldBe -1
    err should not include "App#f"

  test("ScriptRunner --effects prints the inferred effect set to stderr"):
    val src = Files.createTempFile("onion-effects-cli-script", ".on")
    Files.writeString(src, source + "\ndef main: void {}\n", StandardCharsets.UTF_8)

    val (exitCode, err) = captureErr:
      new ScriptRunner().run(Array("--effects", src.toString))

    exitCode shouldBe 0
    err should include("App#f")
    err should include("read")
