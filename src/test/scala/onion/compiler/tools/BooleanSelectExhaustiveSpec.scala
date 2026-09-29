package onion.compiler.tools

import onion.tools.Shell

/**
 * `select b { case true: X; case false: Y }` over a Boolean scrutinee with
 * both literals and no `else` clause must be treated as exhaustive, so the
 * expression yields the LUB of the branch types rather than VOID.
 *
 * Previously the exhaustiveness checker only handled sealed class hierarchies
 * and enum constants; it fell through to `case _ =>` for BasicType.BOOLEAN,
 * left `isExhaustive = false`, and degraded the expression type to VOID,
 * causing a spurious E0020 ("cannot return a value") at the call site.
 */
class BooleanSelectExhaustiveSpec extends AbstractShellSpec {

  describe("select over Boolean scrutinee") {

    it("expression-body form works with both case true and case false") {
      val result = shell.run(
        """
          |def classify(b: Boolean): String = select b {
          |  case true:  "yes"
          |  case false: "no"
          |}
          |def main(): void {
          |  IO::println(classify(true))
          |  IO::println(classify(false))
          |}
        """.stripMargin,
        "BoolSelect.on",
        Array()
      )
      assert(Shell.Failure(-1) != result)
    }

    it("assigns correct values when scrutinee is true") {
      val result = shell.run(
        """
          |def classify(b: Boolean): String = select b {
          |  case true:  "yes"
          |  case false: "no"
          |}
          |static def main(args: String[]): String {
          |  return classify(true)
          |}
        """.stripMargin,
        "BoolSelectTrue.on",
        Array()
      )
      assert(Shell.Success("yes") == result)
    }

    it("assigns correct values when scrutinee is false") {
      val result = shell.run(
        """
          |def classify(b: Boolean): String = select b {
          |  case true:  "yes"
          |  case false: "no"
          |}
          |static def main(args: String[]): String {
          |  return classify(false)
          |}
        """.stripMargin,
        "BoolSelectFalse.on",
        Array()
      )
      assert(Shell.Success("no") == result)
    }

    it("works in block-body with intermediate val") {
      val result = shell.run(
        """
          |static def main(args: String[]): String {
          |  val b: Boolean = 3 > 2
          |  val label: String = select b {
          |    case true:  "gt"
          |    case false: "le"
          |  }
          |  return label
          |}
        """.stripMargin,
        "BoolSelectBlock.on",
        Array()
      )
      assert(Shell.Success("gt") == result)
    }

    it("works with return select b form") {
      val result = shell.run(
        """
          |def toStr(b: Boolean): String {
          |  return select b {
          |    case true:  "T"
          |    case false: "F"
          |  }
          |}
          |static def main(args: String[]): String {
          |  return toStr(false)
          |}
        """.stripMargin,
        "BoolSelectReturn.on",
        Array()
      )
      assert(Shell.Success("F") == result)
    }

    it("still degrades to void when only case true is present") {
      val result = shell.run(
        """
          |def classify(b: Boolean): String = select b {
          |  case true: "yes"
          |}
          |static def main(args: String[]): String { return classify(true) }
        """.stripMargin,
        "BoolSelectMissingFalse.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still works with else clause (pre-existing behaviour)") {
      val result = shell.run(
        """
          |def classify(b: Boolean): String = select b {
          |  case true: "yes"
          |  else:      "no"
          |}
          |static def main(args: String[]): String { return classify(true) }
        """.stripMargin,
        "BoolSelectElse.on",
        Array()
      )
      assert(Shell.Success("yes") == result)
    }

    it("returns Int when both branches produce Int") {
      val result = shell.run(
        """
          |static def main(args: String[]): Int {
          |  val b: Boolean = 1 == 1
          |  return select b {
          |    case true:  42
          |    case false: 0
          |  }
          |}
        """.stripMargin,
        "BoolSelectInt.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }
}
