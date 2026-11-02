package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

/**
 * A write that breaks a `history` is seen wherever the method makes it (#2597, RFC #2557):
 * inside a lambda the method creates, and on another instance (`o.t = ...`). Before, both were
 * silent, the same as a proved history.
 */
class HistoryHiddenWriteSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w24: Seq[String], w24Lines: Seq[Int] = Nil)

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(w => Option(w.location).map(_.line).getOrElse(0)))
  }

  private def clock(methods: String, ty: String = "Int", zero: String = "0", extra: String = "") =
    s"""class Clock {
       |protected:
       |  var t: $ty
       |  history t >= old(t)
       |public:
       |  def this { t = $zero }
       |$methods
       |}
       |$extra""".stripMargin

  describe("a write inside a lambda") {
    it("is E0109 when it lowers the field") {
      val o = compile(clock("  def back(): void { val f: () -> void = () -> { t = t - 1 }; f() }"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is E0109 in a trailing lambda, on a Long field too") {
      val o = compile(clock("  def back(xs: List[Int]): void { xs.forEach { x -> t -= 1L } }", ty = "Long", zero = "0L"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is W0024 when it raises the field, since nothing checks a lambda's write") {
      val o = compile(clock("  def tick(): void { val f: () -> void = () -> { t = t + 1 }; f() }"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("tick") && m.contains("t >= old(t)")), o.toString)
    }

    it("is not a write of the field when the lambda declares its own t") {
      val o = compile(clock("  def back(): void { val f: () -> void = () -> { var t: Int = 5; t = t - 1 }; f() }"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.isEmpty, o.toString)
    }
  }

  describe("a write to another instance") {
    it("is E0109 when it lowers o.t, o a parameter of the class's type") {
      val o = compile(clock("  def other(o: Clock): void { o.t = o.t - 1 }"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is E0109 for o.t-- and o.t -= 1, and for a local declared with the class") {
      val o1 = compile(clock("  def other(o: Clock): void { o.t-- }"))
      assert(o1.errors.contains("E0109"), o1.toString)
      val o2 = compile(clock("  def other(p: Clock): void { val o: Clock = p; o.t -= 1 }"))
      assert(o2.errors.contains("E0109"), o2.toString)
    }

    it("is W0024 when it raises o.t, which nothing checks") {
      val o = compile(clock("  def merge(o: Clock): void { o.t = o.t + 1 }"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("merge") && m.contains("t >= old(t)")), o.toString)
    }

    it("is W0024 when the receiver's class is not written down") {
      val o = compile(clock("  def first(cs: List[Clock]): void { cs[0].t = 7 }"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(_.contains("first")), o.toString)
    }

    it("is not a write of the field when o is another class with a field of the same name") {
      val other = "class Point {\npublic:\n  var t: Int\n  def this { t = 0 }\n}"
      val o = compile(clock("  def move(p: Point): void { p.t = p.t - 1 }", extra = other))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.isEmpty, o.toString)
    }
  }

  // The panel's reproductions on #2599: a receiver the checker cannot settle is W0024, never a
  // false E0109 and never silence; a `T?`, `C[...]` or alias receiver resolves to its class.
  describe("a receiver the checker cannot settle") {
    val point = "class Point {\npublic:\n  var t: Int\n  def this { t = 0 }\n}"
    def eqClock(methods: String, extra: String = "") =
      s"""class Clock {
         |protected:
         |  var t: Int
         |  history t == old(t)
         |public:
         |  def this { t = 0 }
         |$methods
         |}
         |$extra""".stripMargin

    it("is W0024, not E0109, for an untyped lambda parameter of another class") {
      val o = compile(eqClock("  def reset(ps: List[Point]): void { ps.forEach { p -> p.t = 0 } }", extra = point))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("reset") && m.contains("`p`")), o.toString)
      assert(o.w24Lines.contains(7), o.toString)
    }

    it("is W0024, not E0109, for a local whose class comes from a call") {
      val o = compile(eqClock("  def mk(): Point = new Point()\n  def reset(): void { val p = mk(); p.t = 5 }", extra = point))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("reset") && m.contains("`p`")), o.toString)
    }

    it("is W0024 for a name declared twice with different classes in one scope") {
      val o = compile(clock("  def back(b: Boolean, q: Clock): void {\n    if b { val o: Point = new Point(); o.t = 1 } else { val o: Clock = q; o.t = o.t - 1 }\n  }", extra =
        "class Point {\npublic:\n  var t: Int\n  def this { t = 0 }\n}"))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.exists(m => m.contains("back") && m.contains("`o`")), o.toString)
    }

    it("says the write is in a lambda, at the write's line") {
      val o = compile(clock("  def tick(): void {\n    val f: () -> void = () -> { t = t + 1 }\n    f()\n  }"))
      val reason = Message("warning.historyNotChecked.reason.lambda", "8")
      assert(o.w24.exists(_.contains(reason)), o.toString)
      assert(o.w24Lines.contains(8), o.toString)
    }

    it("is E0109 through a nullable receiver of the class") {
      val o = compile(clock("  def back(o: Clock?): void { if o != null { o.t = o.t - 1 } }"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is E0109 through a generic receiver of the class") {
      val src =
        """class Box[T] {
          |protected:
          |  var t: Int
          |  history t >= old(t)
          |public:
          |  def this { t = 0 }
          |  def back(o: Box[T]): void { o.t = o.t - 1 }
          |}""".stripMargin
      val o = compile(src)
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("is E0109 through a type alias of the class") {
      val o = compile(clock("  def back(o: C): void { o.t = o.t - 1 }", extra = "type C = Clock"))
      assert(o.errors.contains("E0109"), o.toString)
    }
  }

  // The panel's reproductions on #2599 at b50adc4: the receiver is resolved where the write is,
  // settled against the class whose history it is, and an object made in the method is fresh.
  describe("the receiver rule") {
    val other = "class Other {\npublic:\n  var t: Int\n  def this { t = 5 }\n}"
    def withPeer(peerType: String, body: String) =
      s"""class Clock {
         |protected:
         |  var t: Int
         |  history t >= old(t)
         |public:
         |  var peer: $peerType
         |  def this { t = 0 }
         |  def dec(): void { $body }
         |}
         |$other""".stripMargin

    it("does not let a lambda's parameter retype a field outside the lambda") {
      val o = compile(withPeer("Other", "peer.t = peer.t - 1; val g: (Clock) -> Int = (peer: Clock) -> { 0 }"))
      assert(!o.errors.contains("E0109"), o.toString)
    }

    it("does not let a lambda's local hide a write outside the lambda") {
      val o = compile(withPeer("Clock", "peer.t = peer.t - 1; val f: () -> void = () -> { val peer: Other = new Other() }"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    it("uses the lambda's own declaration inside the lambda") {
      val o = compile(withPeer("Other", "val f: () -> void = () -> { val peer: Clock = self; peer.t = peer.t - 1 }; f()"))
      assert(o.errors.contains("E0109"), o.toString)
    }

    val family =
      """class Base {
        |public:
        |  var t: Int
        |  def this { t = 0 }
        |}
        |class Plain extends Base {
        |public:
        |  def this { }
        |}
        |class Clock extends Base {
        |  history t >= old(t)
        |public:
        |  def this { }
        |  def tick(): void { t = t + 1 }
        |  %s
        |}""".stripMargin

    it("is not a write of the field through a sibling subclass") {
      val o = compile(family.format("def resetPlain(o: Plain): void { o.t = o.t - 1 }"))
      assert(!o.errors.contains("E0109"), o.toString)
      assert(o.w24.isEmpty, o.toString)
    }

    it("is W0024, not E0109, through a superclass that may or may not be the class") {
      val o = compile(family.format("def resetBase(o: Base): void { o.t = o.t - 1 }"))
      assert(!o.errors.contains("E0109"), o.toString)
      assert(o.w24.exists(m => m.contains("resetBase") && m.contains("`o`")), o.toString)
    }

    it("is E0109 through a superclass whose own history names the field") {
      val src =
        """class Base {
          |public:
          |  var t: Int
          |  history t >= old(t)
          |  def this { t = 0 }
          |}
          |class Clock extends Base {
          |public:
          |  def this { }
          |  def resetBase(o: Base): void { o.t = o.t - 1 }
          |}""".stripMargin
      val o = compile(src)
      assert(o.errors.contains("E0109"), o.toString)
    }

    // `tick` wraps at Int.MAX_VALUE, which the solver rightly reports (E0112); only E0109 is asserted.
    val tick = "  def tick(): void { t = t + 1 }\n"

    it("is not a write when the method made the object with new (a factory)") {
      val o1 = compile(clock(tick + "  static def startingAt5(): Clock { val c = new Clock(); c.t = 5; return c }"))
      assert(!o1.errors.contains("E0109"), o1.toString)
      val o2 = compile(clock(tick + "  static def zero(): Clock { val c: Clock = new Clock(); c.t = 0; return c }"))
      assert(!o2.errors.contains("E0109"), o2.toString)
      val o3 = compile(clock(tick + "  static def mk(): Clock { var c = new Clock(); c.t = 0; return c }"))
      assert(!o3.errors.contains("E0109"), o3.toString)
    }

    it("is a write again once a var made with new is given another object") {
      val o = compile(clock(tick + "  def mix(q: Clock): void { var c = new Clock(); c = q; c.t = 0 }"))
      assert(o.errors.contains("E0109"), o.toString)
    }
  }
}
