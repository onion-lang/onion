package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for selector-based aggregation added to `onion.Colls` — sumBy, averageBy,
 * maxBy, minBy (chainable List extensions) — and `onion.Rand::sample`.
 */
class CollsAggregateBySpec extends AbstractShellSpec {

  private def runInt(prelude: String, body: String, expect: Shell.Result): Unit = {
    val src =
      prelude + "\nclass Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "AggregateBy.on", Array()))
  }

  private def runStr(prelude: String, body: String, expect: Shell.Result): Unit = {
    val src =
      prelude + "\nclass Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "AggregateByStr.on", Array()))
  }

  private val person = "record Person(name: String, age: Int)"

  describe("Colls selector aggregation") {
    it("sumBy and averageBy over a numeric selector") {
      runInt(
        person,
        "val ps = [new Person(\"A\", 30), new Person(\"B\", 45), new Person(\"C\", 15)]\n" +
        "return (Colls::sumBy(ps, (p) -> p.age()) as Int) + (ps.averageBy((p) -> p.age()) as Int)",
        Shell.Success(120)) // 90 + 30
    }

    it("maxBy and minBy return the element, not the key") {
      runStr(
        person,
        "val ps = [new Person(\"Alice\", 30), new Person(\"Bob\", 45), new Person(\"Carol\", 25)]\n" +
        "return ps.maxBy((p) -> p.age()).name() + \"|\" + ps.minBy((p) -> p.age()).name()",
        Shell.Success("Bob|Carol"))
    }

    it("sumBy chains after filter") {
      runInt(
        person,
        "val ps = [new Person(\"A\", 30), new Person(\"B\", 45), new Person(\"C\", 15)]\n" +
        "return ps.filter { p => (p as Person).age() > 20 }.sumBy((p) -> p.age()) as Int",
        Shell.Success(75)) // 30 + 45
    }
  }

  describe("Rand::sample") {
    it("draws n elements, capped at the list size") {
      runInt(
        "import { onion.Rand }",
        "val xs = [1, 2, 3, 4, 5]\n" +
        "return Rand::sample(xs, 3).size() + Rand::sample(xs, 100).size() + Rand::sample(xs, 0).size()",
        Shell.Success(8)) // 3 + 5 + 0
    }

    it("returns distinct elements from the source") {
      runInt(
        "import { onion.Rand }",
        "val xs = [1, 2, 3, 4, 5]\n" +
        "val picked = Rand::sample(xs, 3)\n" +
        "var ok = 0\n" +
        "foreach p: Int in picked { if xs.contains(p) { ok = ok + 1 } }\n" +
        "return ok",
        Shell.Success(3))
    }
  }
}
