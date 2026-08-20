package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test: `select` with a type-pattern case followed by an empty `else:`
 * branch incorrectly executed the matched-case body for ALL inputs, not just the
 * matching ones.
 *
 * Repro:
 *   select flag {
 *     case l is LowCarb: found = true
 *     else:                            // empty body — previously caused found=true always
 *   }
 */
class SelectEmptyElseSpec extends AbstractShellSpec {
  describe("select with empty else: body") {
    it("does NOT execute the matched-case body when the else branch is taken (type pattern + empty else)") {
      val r = shell.run(
        """
          |enum Flag {
          |  case Vegan
          |  case LowCarb(max: Int)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val flags: List[Flag] = [new Vegan() as Flag, new LowCarb(5) as Flag, new Vegan() as Flag]
          |    var trueCount: Int = 0
          |    foreach f: Flag in flags {
          |      var found: Boolean = false
          |      select f {
          |        case l is LowCarb: found = true
          |        else:
          |      }
          |      if found { trueCount++ }
          |    }
          |    return "" + trueCount
          |  }
          |}
          |""".stripMargin, "None", Array())
      // Only the 1 LowCarb element should set found=true; Vegan elements should not
      assert(Shell.Success("1") == r)
    }

    it("does NOT execute the matched-case body when the else branch is taken (value pattern + empty else)") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var count: Int = 0
          |    foreach x: Int in [1, 2, 3, 2, 1] {
          |      select x {
          |        case 2: count++
          |        else:
          |      }
          |    }
          |    return "" + count
          |  }
          |}
          |""".stripMargin, "None", Array())
      // Only the two 2s should increment count
      assert(Shell.Success("2") == r)
    }

    it("empty else does not interfere with preceding case body in a 3-arm select") {
      val r = shell.run(
        """
          |enum Color { case Red; case Green; case Blue }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var reds: Int = 0
          |    var greens: Int = 0
          |    val colors: List[Color] = [new Red() as Color, new Green() as Color, new Red() as Color, new Blue() as Color]
          |    foreach c: Color in colors {
          |      select c {
          |        case r is Red:   reds++
          |        case g is Green: greens++
          |        else:
          |      }
          |    }
          |    return "" + reds + "|" + greens
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("2|1") == r)
    }
  }
}
