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

  private def accountProgram(withdraw: String) =
    s"""class Account {
       |  invariant balance >= 0
       |  history balance <= old(balance) || balance >= 0
       |public:
       |  var balance: Int
       |  def this { balance = 10 }
       |  def withdraw(x: Int): void { $withdraw }
       |}
       |val a = new Account()
       |a.withdraw(Integer::parseInt(args[0]))
       |println(a.balance)
       |""".stripMargin

  test("--contracts runtime keeps an invariant that holds"):
    val path = writeScript(accountProgram("balance = balance - x"))
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path, "4")))
    code shouldBe 0
    out should include("6")

  test("--contracts runtime throws when a method leaves the invariant false"):
    val path = writeScript(accountProgram("balance = balance - x"))
    val chain = violation(Array("--contracts", "runtime", path, "50"))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("invariant (on exit) failed")), chain.map(_.getMessage).toString)

  private def monotone(body: String) =
    s"""class Counter {
       |  history n >= old(n)
       |public:
       |  var n: Int
       |  def this { n = 5 }
       |  def change(): void { $body }
       |}
       |val c = new Counter()
       |c.change()
       |println(c.n)
       |""".stripMargin

  test("--contracts runtime keeps a history that holds"):
    val path = writeScript(monotone("n = n + 1"))
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path)))
    code shouldBe 0
    out should include("6")

  // `n = n - 1` is caught at compile time (E0109). A step the compiler cannot see through is not,
  // and it is exactly what the run-time guard is for.
  test("--contracts runtime throws when a method breaks the history in a way the compiler cannot see"):
    val path = writeScript(monotone("n = n - Integer::parseInt(\"1\")"))
    val chain = violation(Array("--contracts", "runtime", path))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("history failed")), chain.map(_.getMessage).toString)

  private def staticProgram(arg: String) =
    s"""class Calc {
       |public:
       |  static def half(x: Int): Int
       |    requires x >= 0
       |    ensures result <= x
       |  {
       |    if x > 100 { return x + 1 }
       |    return x / 2
       |  }
       |}
       |println(Calc::half($arg))
       |""".stripMargin

  test("--contracts runtime guards a static method: a true ensures passes"):
    val path = writeScript(staticProgram("10"))
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path)))
    code shouldBe 0
    out should include("5")

  test("--contracts runtime guards a static method: a false ensures throws"):
    val path = writeScript(staticProgram("200"))
    val chain = violation(Array("--contracts", "runtime", path))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("postcondition failed")), chain.map(_.getMessage).toString)

  test("--contracts runtime guards a static method: a false requires throws"):
    val path = writeScript(staticProgram("-4"))
    val chain = violation(Array("--contracts", "runtime", path))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("precondition failed")), chain.map(_.getMessage).toString)

  private def genericProgram(body: String) =
    s"""class Box {
       |public:
       |  def keep[T](x: T): T
       |    ensures result == x
       |  { $body }
       |}
       |println(new Box().keep("hello"))
       |""".stripMargin

  test("--contracts runtime guards a generic method: a true ensures passes"):
    val path = writeScript(genericProgram("return x"))
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path)))
    code shouldBe 0
    out should include("hello")

  test("--contracts runtime guards a generic method: a false ensures throws"):
    val path = writeScript(
      """class Box {
        |public:
        |  def pick[T](x: T, y: T): T
        |    ensures result == x
        |  { return y }
        |}
        |println(new Box().pick("a", "b"))
        |""".stripMargin)
    val chain = violation(Array("--contracts", "runtime", path))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("postcondition failed")), chain.map(_.getMessage).toString)

  private def variadicProgram(arg: String) =
    s"""class Sum {
       |public:
       |  def total(xs: Int...): Int
       |    requires xs.length > 0
       |    ensures result >= 0
       |  {
       |    var s = 0
       |    foreach x: Int in xs { s = s + x }
       |    return s
       |  }
       |}
       |println(new Sum().total($arg))
       |""".stripMargin

  test("--contracts runtime guards a variadic method: a true contract passes"):
    val path = writeScript(variadicProgram("1, 2, 3"))
    val (code, out) = captureOut(new ScriptRunner().run(Array("--contracts", "runtime", path)))
    code shouldBe 0
    out should include("6")

  test("--contracts runtime guards a variadic method: a false ensures throws"):
    val path = writeScript(variadicProgram("1, -5"))
    val chain = violation(Array("--contracts", "runtime", path))
    assert(chain.exists(t => t.getMessage != null && t.getMessage.contains("postcondition failed")), chain.map(_.getMessage).toString)

