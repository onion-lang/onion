package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A `history` declared in a subclass binds the methods it inherits without overriding them (#2603).
 * Under `class Up extends Base { history n >= old(n) }`, an inherited `Base.dec` that lowers `n`
 * breaks the history on every `Up`. It is E0109 at the write when the syntax settles it, and W0024
 * when it does not; before, it was silent.
 */
class HistoryInheritedMethodSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], messages: Seq[String], w24: Seq[String])

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "H.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.errors.map(_.message),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message))
  }

  private def program(baseMethod: String, upBody: String = "") =
    s"""class Base {
       |protected:
       |  var n: Int
       |public:
       |  def this { n = 10 }
       |  $baseMethod
       |  def get(): Int = n
       |}
       |class Up extends Base {
       |  history n >= old(n)
       |public:
       |  def inc(): void requires n < 1000 { n = n + 1 }
       |  $upBody
       |}
       |val u = new Up(); u.inc(); println(u.get())""".stripMargin

  it("is E0109 for an inherited method that lowers the field (the issue's repro)") {
    val o = compile(program("def dec(): void { n = n - 1 }"))
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(m => m.contains("Up") && m.contains("Base") && m.contains("dec")), o.toString)
  }

  it("is E0109 for an inherited `n--` and `n -= 2`") {
    assert(compile(program("def dec(): void { n-- }")).errors == Seq("E0109"))
    assert(compile(program("def dec(): void { n -= 2 }")).errors == Seq("E0109"))
  }

  it("is E0109 for an inherited reset to a constant, since `inc` raises the field") {
    val o = compile(program("def reset(): void { n = 0 }"))
    assert(o.errors == Seq("E0109"), o.toString)
  }

  it("accepts the inherited method once the subclass overrides it") {
    val o = compile(program("def dec(): void { n = n - 1 }", "def dec(): void { }"))
    assert(o.errors.isEmpty, o.toString)
  }

  it("accepts an inherited method that only raises or reads the field") {
    val o = compile(program("def bump(): void { n = n + 2 }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.isEmpty, o.toString)
  }

  it("warns W0024 for an inherited write the syntax does not settle") {
    val o = compile(program("def scale(k: Int): void { n = n * k }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.exists(m => m.contains("Base.scale") && m.contains("Up")), o.toString)
  }

  it("reports a history the superclass declares itself once, at the superclass") {
    val src =
      """class Base {
        |  history n >= old(n)
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |}
        |class Up extends Base {
        |public:
        |  def inc(): void { n = n + 1 }
        |}""".stripMargin
    assert(compile(src).errors == Seq("E0109"))
  }

  it("follows an inherited method two levels up, and stops at the nearest override") {
    val src =
      """class A {
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |  def drop(): void { n = n - 5 }
        |}
        |class B extends A {
        |public:
        |  def drop(): void { }
        |}
        |class C extends B {
        |  history n >= old(n)
        |}""".stripMargin
    val o = compile(src)
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(_.contains("dec")) && !o.messages.exists(_.contains("drop")), o.toString)
  }
}
