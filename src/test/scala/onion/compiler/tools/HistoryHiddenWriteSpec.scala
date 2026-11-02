package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

/**
 * A write that breaks a `history` is seen wherever the method makes it (#2597, RFC #2557):
 * inside a lambda the method creates. A write of a contract field through any other object
 * (`o.t = ...`) is E0110 (design 3.2, rule 2; #2599). Before, both were silent, the same as a
 * proved history.
 */
class HistoryHiddenWriteSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w24: Seq[String], w24Lines: Seq[Int] = Nil, errorColumns: Seq[Int] = Nil)

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(w => Option(w.location).map(_.line).getOrElse(0)),
      r.diagnostics.errors.map(e => Option(e.location).map(_.column).getOrElse(0)))
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

  // Design 3.2, rule 2 (#2599): a field an `invariant` or `history` names is written only through
  // `this`. Every receiver the panel found on #2599 is the same error, whatever its declared type.
  describe("a write of a contract field through another object") {
    def rejected(src: String): Unit = {
      val o = compile(src)
      assert(o.errors.contains("E0110"), o.toString)
      assert(!o.errors.contains("E0109"), o.toString)
    }
    val tick = "  def tick(): void { t = t + 1 }\n"
    val point = "class Point {\npublic:\n  var t: Int\n  def this { t = 0 }\n}"

    it("is E0110 through a parameter or local of the class, lowering or raising") {
      rejected(clock("  def other(o: Clock): void { o.t = o.t - 1 }"))
      rejected(clock("  def other(o: Clock): void { o.t-- }"))
      rejected(clock("  def other(p: Clock): void { val o: Clock = p; o.t -= 1 }"))
      rejected(clock("  def merge(o: Clock): void { o.t = o.t + 1 }"))
    }

    it("is E0110 through a receiver whose class the syntax does not give") {
      rejected(clock("  def first(cs: List[Clock]): void { cs[0].t = 7 }"))
      rejected(clock("  def reset(cs: List[Clock]): void { cs.forEach { p -> p.t = 0 } }"))
      rejected(clock("  def mk(): Clock = new Clock()\n  def reset(): void { mk().t = 5 }"))
    }

    it("is E0110 through a nullable, generic or aliased receiver") {
      rejected(clock("  def back(o: Clock?): void { if o != null { o.t = o.t - 1 } }"))
      rejected(clock("  def back(o: C): void { o.t = o.t - 1 }", extra = "type C = Clock"))
      rejected(
        """class Box[T] {
          |protected:
          |  var t: Int
          |  history t >= old(t)
          |public:
          |  def this { t = 0 }
          |  def back(o: Box[T]): void { o.t = o.t - 1 }
          |}""".stripMargin)
    }

    it("is E0110 in a factory, also with the val declared in two branches (panel, 10:14)") {
      rejected(clock(tick + "  static def startingAt5(): Clock { val c = new Clock(); c.t = 5; return c }"))
      rejected(clock(tick + "  static def mk(): Clock { var c = new Clock(); c.t = 0; return c }"))
      rejected(clock(tick +
        "  static def mk(b: Boolean): Clock {\n    if b { val c = new Clock(); c.t = 0; return c } else { val c = new Clock(); c.t = 1; return c }\n  }"))
    }

    it("is E0110 through a foreach variable or a type-pattern binding that shares a field's name") {
      val withPeer = (body: String) =>
        s"""class Clock {
           |protected:
           |  var t: Int
           |  history t >= old(t)
           |public:
           |  var peer: Point
           |  def this { t = 0; peer = new Point() }
           |  def dec(cs: List[Clock], x: Object): void { $body }
           |}
           |$point""".stripMargin
      rejected(withPeer("foreach peer: Clock in cs { peer.t = peer.t - 1 }"))
      rejected(withPeer("select x { case peer is Clock: peer.t = 0\n else: }"))
    }

    it("is E0110 through a superclass or a sibling subclass of the class") {
      val family =
        """class Base {
          |protected:
          |  var t: Int
          |public:
          |  def this { t = 0 }
          |}
          |class Plain extends Base {
          |public:
          |  def this { }
          |  def resetPlain(o: Plain): void { o.t = 0 }
          |}
          |class Clock extends Base {
          |  history t >= old(t)
          |public:
          |  def this { }
          |  def tick(): void { t = t + 1 }
          |  def resetBase(o: Base): void { o.t = o.t - 1 }
          |}""".stripMargin
      val o = compile(family)
      assert(o.errors.count(_ == "E0110") == 2, o.toString)
      assert(!o.errors.contains("E0109"), o.toString)
    }

    it("is E0110 from a class outside the hierarchy, from a constructor, and for an invariant") {
      val open =
        """class Clock {
          |public:
          |  var t: Int
          |  history t >= old(t)
          |  def this { t = 0 }
          |}
          |class Rewinder {
          |public:
          |  def this { }
          |  def rewind(c: Clock): void { c.t = c.t - 1 }
          |}""".stripMargin
      rejected(open)
      rejected(clock("  def this(o: Clock) { t = 0; o.t = 0 }"))
      rejected(
        """class Account {
          |protected:
          |  var balance: Int
          |  invariant balance >= 0
          |public:
          |  def this { balance = 0 }
          |  def drain(o: Account): void { o.balance = 0 }
          |}""".stripMargin)
    }

    it("points at the receiver, not at the dot") {
      val o = compile(clock("  def other(o: Clock): void { o.t = o.t - 1 }"))
      assert(o.errors == Seq("E0110"), o.toString)
      assert(o.errorColumns == Seq("  def other(o: Clock): void { ".length + 1), o.toString)
    }
  }

  describe("what rule 2 still allows") {
    it("checks a write through this or self like a bare one") {
      val o1 = compile(clock("  def back(): void { this.t = this.t - 1 }"))
      assert(o1.errors == Seq("E0109"), o1.toString)
      val o2 = compile(clock("  def back(): void { self.t = self.t - 1 }"))
      assert(o2.errors == Seq("E0109"), o2.toString)
    }

    it("does not count t++, this.t++ or t += 1 as another object") {
      val o = compile(clock("  def a(): void { t++ }\n  def b(): void { this.t++ }\n  def c(): void { t += 1 }\n  def d(): void { self.t += 1 }"))
      assert(!o.errors.contains("E0110"), o.toString)
      assert(!o.errors.contains("E0109"), o.toString)
    }

    it("reads another object's contract field freely") {
      val o = compile(clock("  def same(o: Clock): Boolean = o.t == t"))
      assert(o.errors.isEmpty, o.toString)
    }

    it("does not count another class's field of the same name") {
      val point = "class Point {\npublic:\n  var t: Int\n  def this { t = 0 }\n}"
      val o = compile(clock("  def move(p: Point): void { p.t = p.t - 1 }", extra = point))
      assert(o.errors.isEmpty, o.toString)
      assert(o.w24.isEmpty, o.toString)
    }

    it("says a lambda's write through this is in a lambda, at the write's line") {
      val o = compile(clock("  def tick(): void {\n    val f: () -> void = () -> { t = t + 1 }\n    f()\n  }"))
      val reason = Message("warning.historyNotChecked.reason.lambda", "8")
      assert(o.w24.exists(_.contains(reason)), o.toString)
      assert(o.w24Lines.contains(8), o.toString)
    }
  }
}
