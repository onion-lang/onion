package onion.compiler.tools

import onion.tools.Shell

/**
 * Over an enum scrutinee, a bare `case CONST:` resolves to `EnumType::CONST`
 * (both for matching and exhaustiveness). A local variable of the same name
 * still takes precedence, so the change is additive.
 */
class EnumSelectBareConstantSpec extends AbstractShellSpec {
  private val op = "enum Op { ADD, SUB }\n"
  describe("bare enum constants in select") {
    it("matches bare constants as a value-producing expression") {
      assert(Shell.Success(3) == shell.run(
        op + "def f(o: Op): Int { return select o { case ADD: 1\n case SUB: 2 } }\ndef main(args: String[]): Int { return f(Op::ADD) + f(Op::SUB) }", "None", Array()))
    }
    it("flags a non-exhaustive bare-constant match") {
      assert(Shell.Failure(-1) == shell.run(
        op + "def f(o: Op): Int { return select o { case ADD: 1 } }", "None", Array()))
    }
    it("still accepts the qualified form") {
      assert(Shell.Success(2) == shell.run(
        op + "def f(o: Op): Int { return select o { case Op::ADD: 1\n case Op::SUB: 2 } }\ndef main(args: String[]): Int { return f(Op::SUB) }", "None", Array()))
    }
    it("does not affect a plain value select") {
      assert(Shell.Success(20) == shell.run(
        "def g(n: Int): Int { return select n { case 1: 10\n case 2: 20\n else: 0 } }\ndef main(args: String[]): Int { return g(2) }", "None", Array()))
    }
  }
}
