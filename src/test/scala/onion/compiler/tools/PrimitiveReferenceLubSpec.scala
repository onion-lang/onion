package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #308: the least-upper-bound of a PRIMITIVE branch
 * and a REFERENCE branch boxes the primitive to its wrapper and computes the
 * reference LUB, instead of taking the first branch's type and rejecting the
 * second (E0000). So `if b { 1 } else { "s" }` merges to Object (Integer|String)
 * rather than failing, and `Integer|Number` merges to Number.
 */
class PrimitiveReferenceLubSpec extends AbstractShellSpec {
  describe("branch LUB of a primitive branch and a reference branch") {
    it("a) Int then, String else, declared Object return, compiles and runs") {
      val result = shell.run(
        """
          | def f(b: Boolean): Object = if b { 1 } else { "s" }
          | static def main(args: String[]): String {
          |   return "" + f(true) + ":" + f(false)
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1:s") == result)
    }

    it("b) String then, Int else (reversed) also merges to Object") {
      val result = shell.run(
        """
          | def g(b: Boolean): Object = if b { "s" } else { 1 }
          | static def main(args: String[]): String {
          |   return "" + g(true) + ":" + g(false)
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("s:1") == result)
    }

    it("c) val bound to an Object-typed if-merge of Int and String") {
      val result = shell.run(
        """
          | static def main(args: String[]): String {
          |   val b = true
          |   val x: Object = if b { 1 } else { "s" }
          |   return "" + x
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("d) Integer and Number branches merge to Number") {
      val result = shell.run(
        """
          | import { java.lang.Number as Num }
          | static def main(args: String[]): String {
          |   val n: Num = (java.lang.Integer::valueOf(5) as Num)
          |   val b = true
          |   val r: Num = if b { 1 } else { n }
          |   return "" + r
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("e) UNCHANGED: both-Int branches stay Int") {
      val result = shell.run(
        """
          | static def main(args: String[]): Int {
          |   val b = true
          |   return if b { 1 } else { 2 }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }
}
