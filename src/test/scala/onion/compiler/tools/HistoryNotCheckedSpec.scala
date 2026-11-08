package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A `history` the checker cannot settle is never silent (#2574, RFC #2557): a write on a
 * `Long`/`Double` field is classified like one on an `Int` (E0109), and a write the checker
 * leaves unsettled gets the warning W0024 naming the method, the field and the history.
 */
class HistoryNotCheckedSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w24: Seq[String])

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message))
  }

  private def clock(ty: String, zero: String, one: String, methods: String) =
    s"""class Clock {
       |protected:
       |  var t: $ty
       |  history t >= old(t)
       |public:
       |  def this { t = $zero }
       |$methods
       |}""".stripMargin

  describe("a write with a Long or Double constant") {
    it("is E0109 when it lowers a Long field, as for an Int") {
      val o = compile(clock("Long", "0L", "1L", "  def rewind(): void { t = t - 1L }"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is E0109 when it lowers a Double field") {
      val o = compile(clock("Double", "0.0", "1.0", "  def rewind(): void { t -= 1.0 }"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is E0109 when a Long constant is set while another method raises the field") {
      val o = compile(clock("Long", "0L", "1L", "  def tick(): void { t = t + 1L }\n  def reset(): void { t = 0L }"))
      assert(o.errors.contains("E0109"), o.toString)
    }
  }

  describe("W0024, a history that is not checked") {
    it("names a Long field the solver does not model") {
      val o = compile(clock("Long", "0L", "1L", "  def tick(): void { t = t + 1L }"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.size == 1, o.toString)
      assert(o.w24.head.contains("tick") && o.w24.head.contains("t >= old(t)") && o.w24.head.contains("Long"), o.toString)
    }

    it("names an Int method whose body the solver does not model") {
      val src =
        """class Counter {
          |  invariant n >= 0 && n < 1000000
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { n = 0 }
          |  def step(k: Int): void { foreach i in 1..k { n = n + 1 } }
          |}""".stripMargin
      val o = compile(src)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("step") && m.contains("n >= old(n)")), o.toString)
    }

    it("names a history that is not of the form x >= old(x), with its line") {
      val src =
        """class Door {
          |protected:
          |  var open: Boolean
          |  history old(open) == false || open
          |public:
          |  def this { open = false }
          |  def openIt(): void { open = true }
          |  def reset(): void { open = false }
          |}""".stripMargin
      val o = compile(src)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.size == 2, o.toString)
      assert(o.w24.exists(_.contains("reset")) && o.w24.forall(_.contains("4")), o.toString)
    }

    it("is not given for a history the solver proved") {
      val src =
        """class Counter {
          |  invariant n >= 0 && n < 1000000
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { n = 0 }
          |  def inc(): void { if n < 1000000 { n = n + 1 } }
          |}""".stripMargin
      assert(compile(src) == Outcome(Nil, Nil))
    }

    // #2602: nothing checks an invariant yet, so a proof that needs one is no proof.
    it("is given when the proof holds only by assuming the class invariant") {
      val src =
        """class Counter {
          |  invariant n >= 0 && n < 1000000
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { n = 0 }
          |  def inc(): void { n = n + 1 }
          |}""".stripMargin
      val o = compile(src)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.size == 1 && o.w24.head.contains("Counter.inc") && o.w24.head.contains("invariant"), o.toString)
    }

    it("is given when a constructor breaks the invariant the proof would rest on (#2602 case 3)") {
      val src =
        """class Counter {
          |  invariant n >= 0 && n < 10
          |  history n >= old(n)
          |protected:
          |  var n: Int
          |public:
          |  def this { n = 2147483647 }
          |  def inc(): void { n = n + 1 }
          |  def get(): Int = n
          |}""".stripMargin
      val o = compile(src)
      assert(o.w24.size == 1 && o.w24.head.contains("Counter.inc"), o.toString)
    }

    // #2671 panel: only a method that writes the field can break its history. A call whose
    // `requires` needs the invariant does not make a read-only method's proof unchecked.
    it("is not given to a method that never writes the field, even when a call in it needs the invariant") {
      val src =
        """class C {
          |  invariant n >= 0 && n < 10
          |  history n >= old(n)
          |protected:
          |  var n: Int
          |  var m: Int
          |public:
          |  def this { n = 0; m = 0 }
          |  def step(k: Int): Int requires k >= 0 && k < 100 ensures result >= 0 && result < 100 { return k }
          |  def readOnly(): void { m = step(n) }
          |  def get(): Int = m
          |}""".stripMargin
      val o = compile(src)
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.isEmpty, o.toString)
    }

    it("is turned off by --Wno W0024") {
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On,
        suppressedWarnings = Set(onion.compiler.WarningCategory.HistoryNotChecked), contracts = ContractMode.Check)
      val src = clock("Long", "0L", "1L", "  def tick(): void { t = t + 1L }")
      val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
      assert(r.diagnostics.warnings.isEmpty && r.diagnostics.errors.isEmpty, r.diagnostics.toString)
    }

    it("is not given for a method that does not write the field") {
      val src =
        """class Clock {
          |protected:
          |  var t: Long
          |  history t >= old(t)
          |public:
          |  def this { t = 0L }
          |  def show(): void { IO::println(t) }
          |}""".stripMargin
      assert(compile(src) == Outcome(Nil, Nil))
    }
  }
}
