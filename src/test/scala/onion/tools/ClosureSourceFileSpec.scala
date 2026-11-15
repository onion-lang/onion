package onion.tools

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * An exception thrown inside a lambda is reported at the lambda's own line, and its frames
 * name the script file (#2804). A closure class used to carry no `SourceFile`, so the JVM
 * printed `Unknown Source` and the runtime report skipped to the line that called it.
 */
class ClosureSourceFileSpec extends AnyFunSuite with Matchers {

  private def script(body: String): java.nio.file.Path = {
    val source = Files.createTempFile("closure-source", ".on")
    Files.writeString(source, body, StandardCharsets.UTF_8)
    source
  }

  private def runReport(source: java.nio.file.Path): (Int, String) = {
    val err = new java.io.ByteArrayOutputStream()
    val savedErr = System.err
    val exit =
      try {
        System.setErr(new java.io.PrintStream(err, true, "UTF-8"))
        ScriptRunner.runMain(Array("--Wno", "unused-parameter", source.toString))
      } finally System.setErr(savedErr)
    (exit, new String(err.toByteArray, StandardCharsets.UTF_8))
  }

  test("a throw inside a lambda is reported at the lambda's line, not the caller's") {
    val source = script(
      """val f: () -> Int = () -> {
        |  throw new RuntimeException("in lambda")
        |}
        |f()
        |""".stripMargin)
    val fileName = source.getFileName.toString

    val (exit, text) = runReport(source)
    exit shouldBe 1
    text should include(s"$fileName:2: error: RuntimeException: in lambda")
    text should include("2 |   throw new RuntimeException(\"in lambda\")")
  }

  test("a closure's frames name the script file under --stacktrace") {
    val source = script(
      """val xs = [1, 2, 3]
        |xs.map { x ->
        |  if x == 2 { throw new IllegalStateException("two") }
        |  x
        |}
        |""".stripMargin)
    val fileName = source.getFileName.toString

    val thrown = intercept[IllegalStateException] {
      ScriptRunner.runMain(Array("--stacktrace", "--Wno", "unused-parameter", source.toString))
    }
    val closureFrames = thrown.getStackTrace.filter(_.getClassName.contains("$Closure"))
    closureFrames should not be empty
    closureFrames.foreach(f => f.getFileName shouldBe fileName)
    closureFrames.exists(_.getLineNumber == 3) shouldBe true
  }
}
