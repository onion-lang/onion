package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** A closed predicate runs when the build runs (RFC #2557, design 7.2): the replacement for `example`. */
class ClosedPredicateSpec extends AnyFunSpec {
  private def codes(src: String, mode: ContractMode = ContractMode.Check): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false, contracts = mode)
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
}
