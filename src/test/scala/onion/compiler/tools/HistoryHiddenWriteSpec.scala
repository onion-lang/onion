package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A write that breaks a `history` is seen wherever the method makes it (#2597, RFC #2557):
 * inside a lambda the method creates, and on another instance (`o.t = ...`). Before, both were
 * silent, the same as a proved history.
 */
class HistoryHiddenWriteSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], w24: Seq[String])

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message))
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

    it("is E0109 for o.t-- and o.t -= 1, and for a local made with new") {
      val o1 = compile(clock("  def other(o: Clock): void { o.t-- }"))
      assert(o1.errors.contains("E0109"), o1.toString)
      val o2 = compile(clock("  def other(): void { val o = new Clock(); o.t -= 1 }"))
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
}
