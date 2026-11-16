package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #3067: a lambda whose result is a generic call with nothing of its own to
 * pin it (`() -> Option::none()`) takes its type from the call's target type.
 * It used to bind the call's type parameter to `Option[Object]` first, so
 * `val q: Option[Int] = o.fold(() -> Option::none(), (s) -> Option::some(..))`
 * was E0000 although the target type was written.
 */
class TargetTypedLambdaResultSpec extends AbstractShellSpec {

  private def run(body: String, name: String) =
    shell.run(
      "import { onion.Option; onion.Result; java.util.List; }\n" + body,
      name,
      Array()
    )

  describe("a lambda's generic result is pinned by the call's target type (#3067)") {
    it("Option.fold with an Option or Result target (the issue's repro)") {
      val result = run(
        """val o: Option[String] = Option::of("hi")
          |val q: Option[Int] = o.fold(() -> Option::none(), (s) -> Option::some(s.length()))
          |val r: Result[Int, String] = o.fold(() -> Result::err("none"), (s) -> Result::ok(s.length()))
          |def main(args: String[]): String = "" + q + " " + r
          |""".stripMargin,
        "FoldTarget.on"
      )
      assert(Shell.Success("Some[value=2] Ok[value=2]") == result)
    }

    it("the empty branch runs and keeps the target's type argument") {
      val result = run(
        """val e: Option[String] = Option::none()
          |val q: Option[Int] = e.fold(() -> Option::none(), (s) -> Option::some(s.length()))
          |val n: Int = q.getOrElse(41) + 1
          |def main(args: String[]): String = "" + q + " " + n
          |""".stripMargin,
        "FoldEmpty.on"
      )
      assert(Shell.Success("None[] 42") == result)
    }

    it("with typed lambda parameters, a nullable target, and collection literals") {
      val result = run(
        """val o: Option[String] = Option::of("hey")
          |val e: Option[String] = Option::none()
          |val a: Option[Int] = o.fold(() -> Option::none(), (s: String) -> Option::some(s.length()))
          |val b: Option[Int]? = o.fold(() -> Option::none(), (s) -> Option::some(s.length()))
          |val xs: List[String] = e.fold(() -> [], (s) -> [s])
          |def main(args: String[]): String = "" + a + " " + b + " " + xs
          |""".stripMargin,
        "FoldVariants.on"
      )
      assert(Shell.Success("Some[value=3] Some[value=3] []") == result)
    }

    it("for a generic top-level function") {
      val result = run(
        """def choose[U](f: () -> U, g: (Int) -> U): U = if 1 > 2 { f() } else { g(5) }
          |val a: Option[Int] = choose(() -> Option::none(), (n) -> Option::some(n))
          |def main(args: String[]): String = "" + a
          |""".stripMargin,
        "ChooseTarget.on"
      )
      assert(Shell.Success("Some[value=5]") == result)
    }

    it("a target that disagrees with the other lambda is still an error") {
      val result = run(
        """val o: Option[String] = Option::of("hi")
          |val q: Option[String] = o.fold(() -> Option::none(), (s) -> Option::some(s.length()))
          |def main(args: String[]): String = "" + q
          |""".stripMargin,
        "FoldWrongTarget.on"
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
