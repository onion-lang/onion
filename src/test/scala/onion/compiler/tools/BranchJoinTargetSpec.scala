package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #3241: two branches of one generic class with different type arguments
 * (`Option[String]`, `Option[Int]`) join to the raw class. A raw type fits any
 * instantiation, so `val c: Option[Int] = if b { other } else { tail }` compiled
 * and failed later with ClassCastException. With an expected type, each branch is
 * now checked against it.
 */
class BranchJoinTargetSpec extends AbstractShellSpec {

  private val header =
    "import { onion.Option; java.util.List; java.util.ArrayList; }\n" +
      "val tail: Option[Int] = Option::none()\n" +
      "val other: Option[String] = Option::some(\"str\")\n"

  private def run(body: String, name: String) =
    shell.run(header + body, name, Array())

  describe("branches whose type arguments differ are checked against the expected type (#3241)") {
    it("if/else assigned to an Option[Int] (the issue's repro)") {
      val result = run(
        """val c: Option[Int] = if 1 < 2 { other } else { tail }
          |def main(args: String[]): String = "" + (c.get() + 1)
          |""".stripMargin,
        "JoinIf.on"
      )
      assert(Shell.Failure(-1) == result)
    }

    it("a nullable target, a method's return, and a select") {
      val nullable = run(
        """val c: Option[Int]? = if 1 < 2 { other } else { tail }
          |def main(args: String[]): String = "" + c
          |""".stripMargin,
        "JoinNullable.on"
      )
      assert(Shell.Failure(-1) == nullable)
      val ret = run(
        """def pick(b: Boolean): Option[Int] = if b { other } else { tail }
          |def main(args: String[]): String = "" + pick(true)
          |""".stripMargin,
        "JoinReturn.on"
      )
      assert(Shell.Failure(-1) == ret)
      val sel = run(
        """val k = 1
          |val c: Option[Int] = select k { case 1: other; else: tail }
          |def main(args: String[]): String = "" + c
          |""".stripMargin,
        "JoinSelect.on"
      )
      assert(Shell.Failure(-1) == sel)
    }

    it("well-typed branches still compile and run") {
      val result = run(
        """val a: Option[Int] = if 1 < 2 { Option::some(4) } else { tail }
          |val b: Option[Int] = if 1 > 2 { Option::some(4) } else { Option::none() }
          |val xs: List[Int] = if 1 < 2 { new ArrayList[Int]() } else { [1, 2] }
          |val o: Object = if 1 < 2 { other } else { tail }
          |val n: Option[Int]? = if 1 > 2 { tail } else { null }
          |val raw = if 1 < 2 { other } else { tail }
          |def main(args: String[]): String = "" + a + " " + b + " " + xs + " " + o + " " + n + " " + raw
          |""".stripMargin,
        "JoinWellTyped.on"
      )
      assert(Shell.Success("Some[value=4] None[] [] Some[value=str] null Some[value=str]") == result)
    }
  }
}
