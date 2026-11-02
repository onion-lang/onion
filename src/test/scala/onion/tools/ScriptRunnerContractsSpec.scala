package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `--contracts <off|check|runtime>` reaches the compiler through the real `onion` entry
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

  test("with --contracts off a contract clause is E0105, not ignored"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "off", path)))
    code should not be 0
    out should include("E0105")

  test("without the flag the clause is accepted: check is the default"):
    val path = writeScript(withContract)
    val (code, _) = captureOut(new ScriptRunner().run(Array(path)))
    code shouldBe 0

  test("--contracts check accepts the clause and runs the script"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "check", path)))
    out should not include "invalid argument"
    out should not include "E0105"
    code shouldBe 0
    out should include("true")

  test("--contracts verify is refused, since no proof mode exists yet"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "verify", path)))
    code should not be 0
    out should include("--contracts")
    out should include("verify")

  private val guarded =
    """class Check {
      |public:
      |  static def positive(x: Int): Int
      |    requires x > 0
      |  { return x }
      |}
      |println(Check::positive(Integer::parseInt(args[0])))
      |""".stripMargin

  test("--contracts runtime throws ContractViolation when a requires clause is false"):
    val path = writeScript(guarded)
    val thrown = intercept[onion.compiler.exceptions.ScriptException]:
      captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path, "-3")))
    // the script's own exception is the cause: a ContractViolation, which is an AssertionError
    val cause = Iterator.iterate[Throwable](thrown)(_.getCause).takeWhile(_ != null).toList
    assert(cause.exists(_.isInstanceOf[onion.ContractViolation]), cause.map(_.getClass.getName).toString)
    thrown.getMessage should include("precondition failed")
    thrown.getMessage should include("requires x > 0")

  test("--contracts runtime lets a call that meets requires through"):
    val path = writeScript(guarded)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path, "5")))
    code shouldBe 0
    out should include("5")

  test("--contracts check adds no guard: a false requires is not enforced at run time"):
    val path = writeScript(guarded)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "check", path, "-3")))
    code shouldBe 0
    out should include("-3")

  test("an unknown mode is reported against the flag"):
    val path = writeScript(withContract)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "always", path)))
    code should not be 0
    out should include("--contracts")

  private val ensuresProgram =
    """class Calc {
      |public:
      |  def half(x: Int): Int
      |    requires x >= 0
      |    ensures result <= x
      |  {
      |    if x > 100 { return x + 1 }
      |    return x / 2
      |  }
      |}
      |println(new Calc().half(Integer::parseInt(args[0])))
      |""".stripMargin

  private def violation(args: Array[String]) =
    val thrown = intercept[onion.compiler.exceptions.ScriptException]:
      captureOut(new ScriptRunner().run(args))
    Iterator.iterate[Throwable](thrown)(_.getCause).takeWhile(_ != null).toList

  test("--contracts runtime lets a result that meets ensures through"):
    val path = writeScript(ensuresProgram)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path, "10")))
    code shouldBe 0
    out should include("5")

  test("--contracts runtime throws ContractViolation when ensures is false, on any return"):
    val path = writeScript(ensuresProgram)
    val chain = violation(Array("--contracts", "runtime", path, "200"))
    assert(chain.exists(_.isInstanceOf[onion.ContractViolation]), chain.map(_.getClass.getName).toString)
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("postcondition failed")), chain.map(_.getMessage).toString)

  test("--contracts runtime still guards requires on a method that also has ensures"):
    val path = writeScript(ensuresProgram)
    val chain = violation(Array("--contracts", "runtime", path, "-4"))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("precondition failed")), chain.map(_.getMessage).toString)

  test("--contracts check adds no guard for ensures"):
    val path = writeScript(ensuresProgram)
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "check", path, "200")))
    code shouldBe 0
    out should include("201")

  private def counterProgram(step: String) =
    s"""class Counter {
       |public:
       |  var n: Int
       |  def this { n = 5 }
       |  def inc(): void
       |    ensures n == old(n) + 1
       |  { n = n + $step }
       |}
       |val c = new Counter()
       |c.inc()
       |println(c.n)
       |""".stripMargin

  test("--contracts runtime saves old(e) at entry: n == old(n) + 1 holds for n = n + 1"):
    val path = writeScript(counterProgram("1"))
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path)))
    code shouldBe 0
    out should include("6")

  test("--contracts runtime throws when old(e) shows the postcondition is false"):
    val path = writeScript(counterProgram("2"))
    val chain = violation(Array("--contracts", "runtime", path))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("postcondition failed")), chain.map(_.getMessage).toString)

