package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** W0026: an `ensures` that `--contracts runtime` cannot guard says so (RFC #2557, design 7.4). */
class ContractNotEnforcedWarningSpec extends AnyFunSpec {
  private def w26(src: String, mode: ContractMode = ContractMode.Runtime): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = mode)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "W.on")))
    assert(!r.hasErrors, r.diagnostics.toString)
    r.diagnostics.warnings.filter(_.category.code == "W0026").map(_.message)
  }

  private def counter(ensures: String) =
    s"""class Counter {
       |public:
       |  var n: Int
       |  def this { n = 0 }
       |  def inc(): void
       |    ensures $ensures
       |  { n = n + 1 }
       |}""".stripMargin

  it("is silent for a generic method: it is guarded too") {
    val src =
      """class Box {
        |public:
        |  def keep[T](x: T): T
        |    ensures result == x
        |  { return x }
        |}""".stripMargin
    assert(w26(src).isEmpty)
  }

  it("is silent for a static method: it is guarded too") {
    val src =
      """class Calc {
        |public:
        |  static def twice(x: Int): Int
        |    ensures result == x * 2
        |  { return x * 2 }
        |}""".stripMargin
    assert(w26(src).isEmpty)
  }

  it("is silent for an ensures that uses old(...): it is saved at entry") {
    assert(w26(counter("n == old(n) + 1")).isEmpty)
  }

  it("is silent when the ensures can be guarded") {
    assert(w26(counter("n >= 0")).isEmpty)
  }

  it("is silent under --contracts check, which guards nothing") {
    val src = "class Calc {\npublic:\n  def twice(x: Int): Int\n    ensures result == x * 2\n  { return x * 2 }\n}"
    assert(w26(src, ContractMode.Check).isEmpty)
  }

  it("is silent about the invariant of a class whose constructor it checks") {
    val src =
      """class Account {
        |  invariant balance >= 0
        |public:
        |  var balance: Int
        |  def this { balance = 10 }
        |}""".stripMargin
    assert(w26(src).isEmpty)
  }

  it("names the invariant of a constructor that has a return of its own") {
    val src =
      """class Account {
        |  invariant balance >= 0
        |public:
        |  var balance: Int
        |  def this(early: Boolean) {
        |    balance = 10
        |    if early { return }
        |    balance = 20
        |  }
        |}""".stripMargin
    val ws = w26(src)
    assert(ws.size == 1 && ws.head.contains("return of its own"), ws.toString)
  }

  it("names the invariant of a record, whose constructor the compiler writes") {
    val src = "record Range(lo: Int, hi: Int) {\n  invariant lo <= hi\n}"
    val ws = w26(src)
    assert(ws.size == 1 && ws.head.contains("record"), ws.toString)
  }

  it("says nothing about a constructor when the class has no invariant") {
    val src = "class Plain {\npublic:\n  var n: Int\n  def this { n = 1 }\n}"
    assert(w26(src).isEmpty)
  }
}
