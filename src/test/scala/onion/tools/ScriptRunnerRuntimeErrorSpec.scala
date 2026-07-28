package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** End-to-end coverage for #450: an uncaught runtime error must read like an
 *  Onion diagnostic, not a raw Java stack trace dump. */
class ScriptRunnerRuntimeErrorSpec extends AnyFunSuite with Matchers:

  private def captureErr(args: Array[String]): (Int, String) = {
    val buffer = new ByteArrayOutputStream()
    val err = new PrintStream(buffer, true, StandardCharsets.UTF_8)
    val exitCode = ScriptRunner.runMainCatching(args, err)
    (exitCode, buffer.toString(StandardCharsets.UTF_8))
  }

  test("an uncaught division by zero is reported without JDK/compiler plumbing") {
    val source = Files.createTempFile("onion-runtime-err", ".on")
    Files.writeString(
      source,
      """def main(): void {
        |  println("" + (1 / 0))
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val (exitCode, stderr) = captureErr(Array(source.toString))

    exitCode shouldBe 1
    stderr should include("division by zero")
    stderr should include(s"${source.getFileName}:2")
    stderr should include("--stacktrace")
    stderr should not include "jdk.internal.reflect"
    stderr should not include "onion.tools.Shell"
    stderr should not include "java.lang.reflect.Method"
  }

  test("--stacktrace opts back into the raw exception (unwrapped, uncaught)") {
    val source = Files.createTempFile("onion-runtime-err-raw", ".on")
    Files.writeString(
      source,
      """def main(): void {
        |  println("" + (1 / 0))
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val buffer = new ByteArrayOutputStream()
    val err = new PrintStream(buffer, true, StandardCharsets.UTF_8)
    intercept[ArithmeticException] {
      ScriptRunner.runMainCatching(Array("--stacktrace", source.toString), err)
    }
  }

  test("a deep non-tail recursion reports stack overflow concisely, not thousands of frames") {
    val source = Files.createTempFile("onion-runtime-err-so", ".on")
    Files.writeString(
      source,
      """def boom(n: Int): Int {
        |  return 1 + boom(n + 1)
        |}
        |def main(): void {
        |  println("" + boom(0))
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val (exitCode, stderr) = captureErr(Array(source.toString))

    exitCode shouldBe 1
    stderr should include("stack overflow")
    stderr.linesIterator.size should be < 10
  }
