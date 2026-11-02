package onion.compiler.contracts

import onion.compiler.AST
import org.scalatest.funspec.AnyFunSpec

/** A clause stored in a `.class` file reads back as the same clause, or is not stored (RFC #2557). */
class ContractTextSpec extends AnyFunSpec {
  private def parse(text: String): AST.Expression = ContractText.read(text).get

  describe("a clause the writer stores") {
    for (clause <- Seq("x > 0", "x >= 0 && x < 100", "result == x * 2", "n >= old(n)", "!(a == b)", "n + 1 <= 10"))
      it(s"reads back as the same text: $clause") {
        val e = parse(clause)
        val stored = ContractText.write(e)
        assert(stored.isDefined, clause)
        assert(ContractText.read(stored.get).map(ExpressionText.of) == Some(ExpressionText.of(e)))
      }
  }

  describe("a clause the writer does not store") {
    it("omits a form it cannot print, rather than storing a different clause") {
      // a lambda is not a form contracts are written in
      val e = parse("(x: Int) -> x + 1")
      assert(ContractText.write(e).isEmpty)
    }
  }

  describe("reading") {
    it("returns none for text that is not an expression") {
      assert(ContractText.read("def {").isEmpty)
    }
  }
}
