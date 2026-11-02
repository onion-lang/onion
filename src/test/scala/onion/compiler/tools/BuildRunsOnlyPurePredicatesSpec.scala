package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * The build runs a contract predicate only when that cannot reach the outside world (#2601,
 * design `docs/design/contracts.md` §6): a closed predicate, or the code an `E0112` replay runs,
 * whose effects go beyond `mutate` is not run, and the warning W0023 says so. A run that does
 * not finish in time is stopped waiting for, with the same warning, so it cannot hang the build.
 */
class BuildRunsOnlyPurePredicatesSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w23: Seq[String], stdout: String)

  private def compile(src: String, timeoutMillis: Long = 10000L): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On,
      contracts = ContractMode.Check, contractTimeoutMillis = timeoutMillis)
    val out = new ByteArrayOutputStream()
    val saved = System.out
    val r =
      try {
        System.setOut(new PrintStream(out, true, "UTF-8"))
        new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "P.on")))
      } finally System.setOut(saved)
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0023").map(_.message), out.toString("UTF-8"))
  }

  describe("a closed predicate with an outside effect") {
    it("is not run, and W0023 names it and the effect") {
      val o = compile("""static def isReady(): Boolean ensures result = { IO::println("BUILD-TIME SIDE EFFECT"); true }""")
      assert(!o.stdout.contains("BUILD-TIME SIDE EFFECT"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.size == 1 && o.w23.head.contains("isReady") && o.w23.head.contains("console"), o.toString)
    }

    it("is not run when the effect comes through a method it calls") {
      val o = compile(
        """def shout(): void { IO::println("CALLEE RAN") }
          |def ok(): Boolean ensures result { shout(); return false }""".stripMargin)
      assert(!o.stdout.contains("CALLEE RAN"), o.toString)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(_.contains("ok")), o.toString)
    }

    it("is not run when it calls something the effect table cannot vouch for") {
      val o = compile("""def bye(): Boolean ensures result { java.lang.System::exit(0); return true }""")
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.exists(w => w.contains("bye")), o.toString)
    }
  }

  describe("a closed predicate that only touches memory") {
    it("still runs: a false one is E0113") {
      val o = compile(
        """def sizes(): Boolean ensures result {
          |  val xs = new java.util.ArrayList[Int]()
          |  xs.add(1)
          |  return xs.size == 2
          |}""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("still runs when it builds a generic record (the constructor of `R[String]` is the program's own)") {
      val o = compile(
        """record R[T](rank: Int, item: T)
          |def ranked(): Boolean ensures result = new R[String](1, "E").rank() == 2""".stripMargin)
      assert(o.errors.contains("E0113"), o.toString)
      assert(o.w23.isEmpty, o.toString)
    }

    it("still runs: a true one is silent") {
      val o = compile("def two(): Boolean ensures result = 1 + 1 == 2")
      assert(o.errors.isEmpty && o.w23.isEmpty, o.toString)
    }
  }

  describe("a closed predicate that does not finish") {
    it("stops the build waiting, with W0023 saying it did not finish") {
      val start = System.nanoTime()
      val o = compile(
        """def spin(): Boolean ensures result {
          |  var i = 0
          |  while true { i = i + 1 }
          |  return true
          |}""".stripMargin, timeoutMillis = 300L)
      val seconds = (System.nanoTime() - start) / 1e9
      assert(seconds < 20, s"took $seconds s")
      assert(o.errors.isEmpty, o.toString)
      assert(o.w23.size == 1 && o.w23.head.contains("spin"), o.toString)
    }
  }

  describe("the E0112 replay of a history counterexample") {
    it("does not run a constructor with an outside effect") {
      val o = compile(
        """class Counter {
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { IO::println("CTOR RAN"); n = 0 }
          |  def inc(): void { n = n + 1 }
          |}""".stripMargin)
      assert(!o.stdout.contains("CTOR RAN"), o.toString)
      assert(!o.errors.contains("E0112"), o.toString)
    }

    it("still replays a constructor that only touches memory (E0112)") {
      val o = compile(
        """class Counter {
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { n = 0 }
          |  def inc(): void { n = n + 1 }
          |}""".stripMargin)
      assert(o.errors.contains("E0112"), o.toString)
    }
  }
}
