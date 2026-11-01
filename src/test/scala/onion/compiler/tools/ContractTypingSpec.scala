package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Typing of contract clauses on a method (RFC #2557, design sections 3.1 to 3.4).
 *
 * Assertions use error codes, which are the same in every locale.
 */
class ContractTypingSpec extends AnyFunSpec {

  private def compile(source: String, mode: ContractMode): Seq[CompileError] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false, contracts = mode)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _                                => Nil
    }
  }

  private def codes(source: String, mode: ContractMode = ContractMode.Runtime): Seq[String] =
    compile(source, mode).flatMap(_.errorCode)

  private val counter =
    """class Counter {
      |  var n: Int
      |public:
      |  def get(): Int ensures result == n { return n }
      |}""".stripMargin

  describe("when contracts are off") {
    it("rejects a clause instead of ignoring it") {
      assert(codes(counter, ContractMode.Off).contains("E0105"))
    }

    it("rejects a clause on a method that has no body too") {
      val src = "interface I {\n  def f(x: Int): Int requires x > 0\n}"
      assert(codes(src, ContractMode.Off).contains("E0105"))
    }

    it("leaves a program without clauses alone") {
      val src = "class A {\npublic:\n  def f(x: Int): Int { return x }\n}"
      assert(codes(src, ContractMode.Off).isEmpty)
    }

    it("reports once per clause so each can be found") {
      val src =
        """class A {
          |public:
          |  def f(x: Int): Int requires x > 0 requires x < 9 ensures result > 0 { return x }
          |}""".stripMargin
      assert(codes(src, ContractMode.Off).count(_ == "E0105") == 3)
    }
  }

  describe("with contracts on") {
    it("accepts well-formed clauses") {
      val src =
        """class A {
          |  var n: Int
          |public:
          |  def inc(by: Int): Int
          |    requires by > 0
          |    ensures result == old(n) + by
          |    ensures result == n
          |  { n = n + by; return n }
          |}""".stripMargin
      assert(codes(src).isEmpty, codes(src).toString)
    }

    it("rejects a clause that is not a Boolean") {
      val src = "class A {\npublic:\n  def f(x: Int): Int requires x { return x }\n}"
      assert(codes(src).nonEmpty)
    }

    it("lets requires see the parameters and ensures see result") {
      val src = "class A {\npublic:\n  def f(x: Int): Int requires x > 0 ensures result > 0 { return x }\n}"
      assert(codes(src).isEmpty, codes(src).toString)
    }

    it("does not give requires a result") {
      val src = "class A {\npublic:\n  def f(x: Int): Int requires result > 0 { return x }\n}"
      assert(codes(src).nonEmpty)
    }

    it("does not give a void method a result") {
      val src = "class A {\npublic:\n  def f(x: Int): void ensures result > 0 { }\n}"
      assert(codes(src).nonEmpty)
    }

    it("puts old() in ensures only") {
      val src = "class A {\n  var n: Int\npublic:\n  def f(): void requires old(n) > 0 { }\n}"
      assert(codes(src).contains("E0107"), codes(src).toString)
    }

    it("leaves old and result ordinary names outside a clause") {
      val src =
        """class A {
          |public:
          |  def old(x: Int): Int { return x }
          |  def f(): Int { val result = old(3); return result }
          |}""".stripMargin
      assert(codes(src).isEmpty, codes(src).toString)
    }
  }

  describe("purity") {
    it("rejects a clause that writes to memory") {
      val src =
        """import { java.util.* }
          |class A {
          |public:
          |  def f(xs: List[Int]): Int requires xs.add(1) { return 0 }
          |}""".stripMargin
      assert(codes(src).contains("E0106"), codes(src).toString)
    }

    it("rejects a clause that does I/O") {
      val src =
        """class A {
          |public:
          |  def f(p: String): Int requires Files::exists(p) { return 0 }
          |}""".stripMargin
      assert(codes(src).contains("E0106"), codes(src).toString)
    }

    it("accepts a call to a pure method of the program") {
      val src =
        """class A {
          |public:
          |  def positive(x: Int): Boolean = x > 0
          |  def f(x: Int): Int requires positive(x) { return x }
          |}""".stripMargin
      assert(codes(src).isEmpty, codes(src).toString)
    }

    it("rejects a call to a method of the program that writes") {
      val src =
        """class A {
          |  var n: Int
          |public:
          |  def bump(): Boolean { n = n + 1; return true }
          |  def f(x: Int): Int requires bump() { return x }
          |}""".stripMargin
      assert(codes(src).contains("E0106"), codes(src).toString)
    }

    it("accepts reading a collection") {
      val src =
        """import { java.util.* }
          |class A {
          |public:
          |  def f(xs: List[Int]): Int requires xs.size > 0 { return xs.get(0) }
          |}""".stripMargin
      assert(codes(src).isEmpty, codes(src).toString)
    }
  }
}
