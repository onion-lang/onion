package onion.compiler.tools

import onion.tools.Shell

/**
 * `Outcome` is the one failure channel for the boundary. Onion currently reports the same
 * conceptual failure — "this external data did not match" — five different ways: `null`
 * on a regex no-match, a second indistinguishable `null` on a conversion failure, a
 * silently dropped line in `parseAll`, a `null` from `fromJson` that discards the parser's
 * position, and `System.exit(1)` from `Cli`.
 *
 * The reason this is not `Result[T, List[Defect]]` is `zip`: `Result` is monadic and
 * short-circuits, so the first bad field hides the rest. A record with three malformed
 * fields must report three defects in one pass, which is a different composition law.
 */
class OutcomeSpec extends AbstractShellSpec {

  private val imports = "import { onion.Outcome; onion.Defect; onion.Origin; }"

  describe("Defect") {
    it("carries where, what was expected and what was found") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val d = new Defect(Origin::at("a.log", 3, 5), "status", "Int", "abc")
            |    return d.path() + "|" + d.expected() + "|" + d.actual() + "|" + d.origin().describe()
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("status|Int|abc|a.log:3:5") == r)
    }

    it("can have no origin, for a failure with no position to point at") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Defect::of("name", "String", "absent").describe()
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("name: expected String, found absent") == r)
    }

    it("puts the position first when it has one, like a compiler does") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Defect::at(Origin::atLine("d.json", 4), "port", "Int", "\\"http\\"").describe()
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("d.json:4: port: expected Int, found \"http\"") == r)
    }
  }

  describe("Outcome") {
    it("holds a value") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return (Outcome::ok(41) as Outcome[JInteger]).getOrElse(0) + 1
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }

    it("falls back when bad") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val o: Outcome[JInteger] = Outcome::bad(Defect::of("x", "Int", "nope"))
            |    return o.getOrElse(7)
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }

    it("maps over a value and leaves defects alone") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val good: Outcome[JInteger] = Outcome::ok(2)
            |    val bad: Outcome[JInteger] = Outcome::bad(Defect::of("x", "Int", "nope"))
            |    val a = good.map { v -> v + 1 }
            |    val b = bad.map { v -> v + 1 }
            |    return "" + a.getOrElse(0) + "/" + b.defects().size + "/" + b.isBad()
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("3/1/true") == r)
    }
  }

  describe("zip accumulates — the reason this type exists") {
    it("combines two values") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val a: Outcome[JInteger] = Outcome::ok(20)
            |    val b: Outcome[JInteger] = Outcome::ok(22)
            |    return a.zip(b) { x, y -> x + y }.getOrElse(0)
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }

    it("reports BOTH failures, not just the first") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val a: Outcome[JInteger] = Outcome::bad(Defect::of("x", "Int", "p"))
            |    val b: Outcome[JInteger] = Outcome::bad(Defect::of("y", "Int", "q"))
            |    return a.zip(b) { p, q -> p + q }.defects().size
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }

    it("keeps defect order left to right, so a report reads like the input") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val a: Outcome[JInteger] = Outcome::bad(Defect::of("first", "Int", "p"))
            |    val b: Outcome[JInteger] = Outcome::bad(Defect::of("second", "Int", "q"))
            |    val ds = a.zip(b) { p, q -> p + q }.defects()
            |    return (ds[0] as Defect).path() + "," + (ds[1] as Defect).path()
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("first,second") == r)
    }
  }

  describe("bind short-circuits — which is why zip is separate") {
    it("stops at the first failure") {
      // bind must short-circuit: the second computation may depend on the first's value,
      // so there is nothing to run it against. That is exactly why accumulation needs
      // its own operation instead of being folded into flatMap.
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val a: Outcome[JInteger] = Outcome::bad(Defect::of("x", "Int", "p"))
            |    return a.flatMap { v -> Outcome::bad(Defect::of("y", "Int", "q")) }.defects().size
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }

    it("works with do notation") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val o = do[Outcome] { x <- Outcome::ok(40); y <- Outcome::ok(2); ret x + y }
            |    return (o as Outcome[JInteger]).getOrElse(0)
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }
  }

  describe("collecting many outcomes") {
    it("keeps the good values and the defects separately") {
      // This is what a per-line parse needs: 995 rows AND 5 defects, not one or the other.
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val os: List[Outcome[JInteger]] =
            |      [Outcome::ok(1), Outcome::bad(Defect::of("a", "Int", "x")), Outcome::ok(3)]
            |    return "" + Outcome::values(os).size + "/" + Outcome::defects(os).size
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("2/1") == r)
    }

    it("all() is all-or-nothing and accumulates every defect") {
      val r = shell.run(
        s"""|$imports
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val good: List[Outcome[JInteger]] = [Outcome::ok(1), Outcome::ok(2)]
            |    val mixed: List[Outcome[JInteger]] =
            |      [Outcome::ok(1), Outcome::bad(Defect::of("a", "Int", "x")),
            |       Outcome::bad(Defect::of("b", "Int", "y"))]
            |    return "" + Outcome::all(good).isOk() + "/" + Outcome::all(mixed).defects().size
            |  }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success("true/2") == r)
    }
  }
}
