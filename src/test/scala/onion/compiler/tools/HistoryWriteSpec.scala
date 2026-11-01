package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * A write that breaks a `history` (RFC #2557, design 4.1; Liskov and Wing, 1994): the rule that
 * catches `ResettableCounter.reset`, which keeps every pre- and postcondition of `Counter`.
 */
class HistoryWriteSpec extends AnyFunSpec {

  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode)
      case _                                => Nil
    }
  }

  private val counter =
    """class Counter {
      |  invariant n >= 0 && n < 1000000
      |  history n >= old(n)
      |public:
      |  var n: Int
      |  def this { n = 0 }
      |  def inc(): void { n = n + 1 }
      |}""".stripMargin

  describe("Counter and its subtypes") {
    it("accepts Counter itself") { assert(codes(counter).isEmpty) }

    it("rejects a subtype whose reset sets n to 0, because inc raises n") {
      val src = counter + "\nclass ResettableCounter extends Counter {\npublic:\n  def reset(): void { n = 0 }\n}"
      assert(codes(src).contains("E0109"))
    }

    it("accepts a subtype that only raises n") {
      val src = counter + "\nclass Fast extends Counter {\npublic:\n  def bump(): void { n = n + 10 }\n}"
      assert(codes(src).isEmpty)
    }

    it("rejects a method that lowers n") {
      val src = counter + "\nclass Down extends Counter {\npublic:\n  def dec(): void { n = n - 1 }\n}"
      assert(codes(src).contains("E0109"))
    }

    it("does not blame a parameter that shares the field's name") {
      val src = counter + "\nclass P extends Counter {\npublic:\n  def f(n: Int): void { n = 0 }\n}"
      assert(codes(src).isEmpty)
    }
  }

  describe("a class whose only write is a constant") {
    it("is fine once an invariant says n stays 0, so n never rises above its start") {
      val src =
        """class Flag {
          |  invariant n == 0
          |  history n >= old(n)
          |public:
          |  var n: Int
          |  def this { n = 0 }
          |  def clear(): void { n = 0 }
          |}""".stripMargin
      assert(codes(src).isEmpty)
    }
  }

  describe("history n == old(n)") {
    it("rejects any write outside a constructor") {
      val src =
        """class P {
          |  history x == old(x)
          |public:
          |  var x: Int
          |  def this { x = 1 }
          |  def move(): void { x = 2 }
          |}""".stripMargin
      assert(codes(src).contains("E0109"))
    }
  }

  describe("a history on an interface") {
    it("binds the implementing class too (design 4.2)") {
      val src =
        """interface Monotone {
          |  history n >= old(n)
          |}
          |class Impl conforms Monotone {
          |public:
          |  var n: Int
          |  def this { n = 5 }
          |  def dec(): void { n = n - 1 }
          |}""".stripMargin
      assert(codes(src).contains("E0109"))
    }
  }
}
