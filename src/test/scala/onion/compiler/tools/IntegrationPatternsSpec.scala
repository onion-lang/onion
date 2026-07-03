package onion.compiler.tools

import onion.tools.Shell

/**
 * End-to-end regression coverage for sophisticated feature combinations that
 * isolated specs do not exercise together: recursive sealed records, type-class
 * dictionaries over collections, monadic do-notation, and enum state machines.
 */
class IntegrationPatternsSpec extends AbstractShellSpec {
  it("evaluates a recursive sealed-record expression tree") {
    val src =
      "sealed interface Expr\nrecord Num(v: Int) <: Expr\nrecord Add(l: Expr, r: Expr) <: Expr\nrecord Mul(l: Expr, r: Expr) <: Expr\n" +
      "def eval(e: Expr): Int { return select e { case Num(v): v\n case Add(l, r): eval(l) + eval(r)\n case Mul(l, r): eval(l) * eval(r) } }\n" +
      "def main(args: String[]): Int { val e: Expr = new Add(new Num(3), new Mul(new Num(4), new Num(5)))\n return eval(e) }"
    assert(Shell.Success(23) == shell.run(src, "None", Array()))
  }

  it("folds a collection through a type-class dictionary (Monoid)") {
    val src =
      "trait Monoid[T] { def empty(): T\n def combine(a: T, b: T): T }\n" +
      "instance Monoid[Integer] { def empty(): Integer = 0\n def combine(a: Integer, b: Integer): Integer = a + b }\n" +
      "def mconcat[T: Monoid](xs: List[T]): T { var acc: T = Monoid[T]::empty()\n foreach x: T in xs { acc = Monoid[T]::combine(acc, x) }\n return acc }\n" +
      "def main(args: String[]): Int { return mconcat([1, 2, 3, 4, 5]) }"
    assert(Shell.Success(15) == shell.run(src, "None", Array()))
  }

  it("short-circuits a do[Option] chain") {
    val src =
      "def parse(s: String): Option[Integer] { try { return Option::some(Integer::parseInt(s)) } catch e: Exception { return Option::none() } }\n" +
      "def process(s: String): Option[Integer] { return do[Option] { n <- parse(s)\n ret n * 2 } }\n" +
      "def main(args: String[]): String { return process(\"21\").getOrElse(-1) + \",\" + process(\"xx\").getOrElse(-1) }"
    assert(Shell.Success("42,-1") == shell.run(src, "None", Array()))
  }

  it("drives an enum state machine through select") {
    val src =
      "enum State { IDLE, RUNNING, PAUSED, STOPPED }\n" +
      "def step(s: State, e: String): State { return select s { case State::IDLE: if e == \"start\" { State::RUNNING } else { s }\n case State::RUNNING: if e == \"stop\" { State::STOPPED } else { s }\n else: s } }\n" +
      "def main(args: String[]): String { var s = State::IDLE\n s = step(s, \"start\")\n s = step(s, \"stop\")\n return s.name() }"
    assert(Shell.Success("STOPPED") == shell.run(src, "None", Array()))
  }
}
