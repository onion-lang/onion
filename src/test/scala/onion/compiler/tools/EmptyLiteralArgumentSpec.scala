package onion.compiler.tools

import onion.tools.Shell

/**
 * An empty collection literal (`[]` / `[:]`) is target-typed at argument position,
 * so `foo([])` binds `[]` to the parameter's element type instead of failing
 * overload resolution (it defaulted to `List[Object]`, which no longer matches
 * `List[String]` under type-argument invariance).
 */
class EmptyLiteralArgumentSpec extends AbstractShellSpec {
  describe("empty literal across call kinds") {
    it("top-level function") {
      assert(Shell.Success(0) == shell.run("def size(xs: List[String]): Int = xs.size()\ndef main(args: String[]): Int { return size([]) }", "None", Array()))
    }
    it("instance method") {
      assert(Shell.Success(0) == shell.run(
        """
          |class C { public: def this {} def take(xs: List[String]): Int = xs.size() }
          |def main(args: String[]): Int { return new C().take([]) }
          |""".stripMargin, "None", Array()))
    }
    it("static method and constructor") {
      assert(Shell.Success(0) == shell.run(
        """
          |class Box {
          |  val xs: List[Int]
          |public:
          |  def this(items: List[Int]) { xs = items }
          |  static def count(ys: List[Int]): Int = ys.size()
          |  def n(): Int = xs.size()
          |}
          |def main(args: String[]): Int { return Box::count([]) + new Box([]).n() }
          |""".stripMargin, "None", Array()))
    }
    it("empty map literal") {
      assert(Shell.Success(0) == shell.run("def keys(m: Map[String, Int]): Int = m.size()\ndef main(args: String[]): Int { return keys([:]) }", "None", Array()))
    }
    it("multiple empty arguments, and target typing is applied") {
      assert(Shell.Success(0) == shell.run("def both(a: List[String], b: List[Int]): Int = a.size() + b.size()\ndef main(args: String[]): Int { return both([], []) }", "None", Array()))
      assert(Shell.Success("none") == shell.run(
        "def firstOr(xs: List[String], d: String): String { if xs.size() > 0 { return xs.get(0) } else { return d } }\ndef main(args: String[]): String { return firstOr([], \"none\") }", "None", Array()))
    }
    it("a non-empty literal with the wrong element type still fails") {
      assert(Shell.Failure(-1) == shell.run("def size(xs: List[String]): Int = xs.size()\ndef main(args: String[]): Int { return size([1, 2]) }", "None", Array()))
    }
    it("an empty literal does not match a non-collection parameter") {
      assert(Shell.Failure(-1) == shell.run("def g(x: String): Int = x.length()\ndef main(args: String[]): Int { return g([]) }", "None", Array()))
    }
  }
}
