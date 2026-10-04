package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #1971: a closure created inside a classic C-style
 * `for var i: Int = 0; i < n; i++ { }` loop captured a single storage cell
 * shared across every iteration, so every closure observed only `i`'s final
 * post-loop value. `i` is reassigned by the loop's own `i++` update, so it is
 * boxed for closure capture; the box was only ever freshly allocated at the
 * `for`-init (the first place codegen sees a `SetLocal` for that slot), and
 * every `i++` thereafter just mutated that same box in place. `foreach` does
 * not have this problem because its element variable is assigned exactly
 * once, lexically inside the loop body, so a fresh box is allocated on every
 * iteration automatically.
 */
class ForLoopClosureCaptureSpec extends AbstractShellSpec {

  describe("a closure capturing a C-style for loop's induction variable") {
    it("sees each iteration's own value, not just the final one (#1971)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val fns: List[Function0[Int]] = []
          |    for var i: Int = 0; i < 5; i++ {
          |      fns.add(() -> i)
          |    }
          |    var result = ""
          |    foreach f: Function0[Int] in fns {
          |      result = result + f() + " "
          |    }
          |    return result.trim()
          |  }
          |}
          |""".stripMargin,
        "ForLoopClosureCapture.on",
        Array()
      )
      assert(Shell.Success("0 1 2 3 4") == result)
    }

    it("still lets a closure mutate the induction variable when it is never reassigned in the body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var last: Int = -1
          |    for var i: Int = 0; i < 3; i++ {
          |      val f = () -> { last = i }
          |      f()
          |    }
          |    return "" + last
          |  }
          |}
          |""".stripMargin,
        "ForLoopClosureCaptureMutation.on",
        Array()
      )
      assert(Shell.Success("2") == result)
    }

    it("does not disturb a plain for loop with no closures") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var sum: Int = 0
          |    for var i: Int = 0; i < 5; i++ {
          |      sum = sum + i
          |    }
          |    return "" + sum
          |  }
          |}
          |""".stripMargin,
        "ForLoopNoClosure.on",
        Array()
      )
      assert(Shell.Success("10") == result)
    }
  }
}
