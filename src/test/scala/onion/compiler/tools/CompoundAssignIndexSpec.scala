package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #249: a compound assignment on an indexed target
 * (`a[i] op= v`) must evaluate the receiver and index sub-expressions exactly
 * once, even though it reads and writes the element. Previously the target was
 * duplicated, so a side-effecting index/receiver ran twice.
 */
class CompoundAssignIndexSpec extends AbstractShellSpec {
  describe("Compound assignment on an indexed target (#249)") {
    it("evaluates a side-effecting array index exactly once") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static var counter: Int = 0
          |  static def idx(): Int {
          |    counter = counter + 1
          |    return 0
          |  }
          |  static def main(args: String[]): Int {
          |    val a: Int[] = new Int[3]
          |    a[0] = 10
          |    a[idx()] += 5
          |    // idx() ran once => counter == 1; a[0] == 15
          |    return counter * 100 + a[0]
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(115) == result)
    }

    it("evaluates a side-effecting array receiver exactly once") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static var rc: Int = 0
          |  static var shared: Int[] = new Int[2]
          |  static def arr(): Int[] {
          |    rc = rc + 1
          |    return shared
          |  }
          |  static def main(args: String[]): Int {
          |    shared[0] = 7
          |    arr()[0] += 3
          |    // arr() ran once => rc == 1; shared[0] == 10
          |    return rc * 100 + shared[0]
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(110) == result)
    }

    it("evaluates a side-effecting List index exactly once") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static var lc: Int = 0
          |  static def li(): Int {
          |    lc = lc + 1
          |    return 0
          |  }
          |  static def main(args: String[]): Int {
          |    val xs = [10, 20, 30]
          |    xs[li()] += 5
          |    // li() ran once => lc == 1; xs[0] == 15
          |    return lc * 100 + (xs[0] as Int)
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(115) == result)
    }

    it("evaluates a side-effecting Map key exactly once") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static var mc: Int = 0
          |  static def k(): String {
          |    mc = mc + 1
          |    return "a"
          |  }
          |  static def main(args: String[]): Int {
          |    val m = ["a": 1, "b": 2]
          |    m[k()] += 10
          |    // k() ran once => mc == 1; m["a"] == 11
          |    return mc * 100 + (m["a"] as Int)
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(111) == result)
    }

    it("evaluates each index of a nested indexed target exactly once") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static var c: Int = 0
          |  static def i(): Int {
          |    c = c + 1
          |    return 0
          |  }
          |  static def main(args: String[]): Int {
          |    val m: Int[][] = new Int[2][2]
          |    m[i()][i()] += 7
          |    // i() ran twice total (once per index), not four times
          |    return c * 100 + m[0][0]
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(207) == result)
    }

    it("still computes plain literal-index compound assignment correctly") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Int[] = new Int[3]
          |    a[1] = 5
          |    a[1] -= 2
          |    a[1] *= 4
          |    return a[1]
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(12) == result)
    }
  }
}
