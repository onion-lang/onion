package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** A closed predicate runs when the build runs (RFC #2557, design 7.2): the replacement for `example`. */
class ClosedPredicateSpec extends AnyFunSpec {
  private def codes(src: String, mode: ContractMode = ContractMode.Check): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = mode)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode)
      case _                                => Nil
    }
  }

  it("accepts a true top-level predicate") {
    assert(codes("def sums(): Boolean\n  ensures result\n  = 1 + 1 == 2\n").isEmpty)
  }

  it("rejects a false top-level predicate with E0113") {
    assert(codes("def sums(): Boolean\n  ensures result\n  = 1 + 1 == 3\n").contains("E0113"))
  }

  it("words a predicate that throws without \"is threw\" (#2605)") {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = ContractMode.Check)
    val src = "def boom(): Boolean\n  ensures result\n  = \"a\".charAt(5) == 'a'\n"
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "C.on"))) match {
      case CompilationOutcome.Failure(errs) =>
        val e = errs.find(_.errorCode.contains("E0113")).getOrElse(fail("no E0113"))
        if (java.util.Locale.getDefault.getLanguage == "ja") assert(e.message.contains("投げました"), e.message)
        else {
          assert(e.message.contains("threw"))
          assert(!e.message.contains("is threw"))
        }
      case other => fail(s"expected failure, got $other")
    }
  }

  it("runs a static predicate of a class") {
    val src = "class A {\npublic:\n  static def ok(): Boolean\n    ensures result\n    = 2 * 3 == 7\n}\n"
    assert(codes(src).contains("E0113"))
  }

  it("runs a static predicate of a record") {
    val src = "record P(x: Int) {\npublic:\n  static def ok(): Boolean\n    ensures result\n    = new P(1).x() == 2\n}\n"
    assert(codes(src).contains("E0113"))
  }

  it("does nothing when contracts are off") {
    assert(!codes("def sums(): Boolean\n  = 1 + 1 == 3\n", ContractMode.Off).contains("E0113"))
  }

  it("samples a predicate with parameters: a true law holds for every sample") {
    assert(codes("def commutes(a: Int, b: Int): Boolean\n  ensures result\n  = a + b == b + a\n").isEmpty)
  }

  it("falsifies a predicate with parameters, with a counterexample (E0113)") {
    assert(codes("def small(a: Int): Boolean\n  ensures result\n  = a < 1000\n").contains("E0113"))
  }

  it("does not pass silently when a parameter type cannot be sampled") {
    val src = "import { java.util.* }\ndef f(x: Thread): Boolean\n  ensures result\n  = true\n"
    assert(codes(src).contains("E0113"))
  }

  it("leaves a Boolean function with a real postcondition alone") {
    val src = "def positive(x: Int): Boolean\n  ensures result == (x > 0)\n  = x > 0\n"
    assert(!codes(src).contains("E0113"))
  }
}
