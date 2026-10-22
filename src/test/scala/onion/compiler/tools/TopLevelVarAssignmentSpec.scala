package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * A top-level script `var` is assigned like a local (issue #2319).
 *
 * It lives as a static field of the script class, so it used to miss two things a
 * local gets: the implicit narrowing of `b += 1B` on a Byte/Short/Char, and a single
 * error when the assigned value does not fit. A mismatch reported the real error and
 * then a bogus "local variable vb is not found" (E0002).
 */
class TopLevelVarAssignmentSpec extends AbstractShellSpec {

  private def errorCodes(source: String): Seq[String] =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 20))
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "S.on")))
      .allErrors
      .map(_.errorCode.getOrElse("?"))
      .toSeq

  private def output(source: String): String = {
    val originalOut = System.out
    val buffer = new java.io.ByteArrayOutputStream()
    System.setOut(new java.io.PrintStream(buffer, true, "UTF-8"))
    val result =
      try shell.run(source, "S.on", Array())
      finally System.setOut(originalOut)
    assert(!result.isInstanceOf[Shell.Failure], s"failed to compile/run:\n$source")
    buffer.toString("UTF-8").trim
  }

  describe("compound assignment on a top-level Byte/Short/Char var") {
    it("narrows a Byte, as inside a function") {
      assert(output(
        "var vb: Byte = 5B\nvb += 1B\nIO::println(vb as Int)") == "6")
    }
    it("narrows a Short through several operators") {
      assert(output(
        "var vs: Short = 5S\nvs -= 1S\nvs *= 2\nIO::println(vs as Int)") == "8")
    }
    it("narrows a Char") {
      assert(output(
        "var c: Char = 'a'\nc += 1\nIO::println(c as Int)") == "98")
    }
    it("wraps like Java") {
      assert(output(
        "var vb: Byte = 100B\nvb += 100B\nIO::println(vb as Int)") == "-56")
    }
    it("narrows when a top-level function assigns the var") {
      assert(output(
        "var vb: Byte = 5B\ndef bump(): void { vb += 2B }\nbump()\nIO::println(vb as Int)") == "7")
    }
  }

  describe("a value that does not fit a top-level var") {
    it("reports only the type mismatch, not E0002") {
      val codes = errorCodes("var vb: Byte = 5B\nvb = vb + 1B\n")
      assert(codes == Seq("E0000"), codes.mkString(", "))
    }
    it("reports only the type mismatch for a String var") {
      val codes = errorCodes("var s: String = \"a\"\ns = 1\n")
      assert(codes == Seq("E0000"), codes.mkString(", "))
    }
  }
}
