package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `--contracts <off|runtime|verify>` reaches the compiler through the real `onion` entry
 * point (RFC #2557). `ContractsFlagSpec` checks the option parser and `configFrom`; this
 * checks the command line a person types, which `sbt runScript` once rejected with
 * "--contracts is an invalid argument".
 */
class ScriptRunnerContractsSpec extends AnyFunSuite with Matchers:
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
    val src = Files.createTempFile("onion-contracts", ".on")
    Files.writeString(src, source, StandardCharsets.UTF_8)
    src.toString

  private val withContract =
    """class Check {
      |public:
      |  static def positive(x: Int): Boolean
      |    ensures result == (x > 0)
      |    = x > 0
      |}
      |println(Check::positive(3))
      |""".stripMargin

  test("without the flag a contract clause is E0105, not ignored"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array(path)))
    code should not be 0
    out should include("E0105")

  test("--contracts runtime accepts the clause and runs the script"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path)))
    out should not include "invalid argument"
    out should not include "E0105"
    code shouldBe 0
    out should include("true")

  test("--contracts verify is accepted too"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "verify", path)))
    out should not include "invalid argument"
    code shouldBe 0

  test("an unknown mode is reported against the flag"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "always", path)))
    code should not be 0
    out should include("--contracts")
