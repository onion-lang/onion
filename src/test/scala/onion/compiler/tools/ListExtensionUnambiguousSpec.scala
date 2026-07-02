package onion.compiler.tools

import onion.tools.Shell

/**
 * Essential list operations resolve unambiguously (they were declared in both
 * onion.Colls and onion.Iterables with identical List signatures, causing E0006).
 */
class ListExtensionUnambiguousSpec extends AbstractShellSpec {
  describe("list take/drop/reverse/first/last") {
    it("take/drop/reverse resolve and compute") {
      val r = shell.run(
        "def main(args: String[]): String { val xs = [1,2,3,4]\n return \"\" + xs.take(2) + xs.drop(2) + xs.reverse() }", "None", Array())
      assert(Shell.Success("[1, 2][3, 4][4, 3, 2, 1]") == r)
    }
    it("first/last resolve") {
      assert(Shell.Success(50) == shell.run(
        "def main(args: String[]): Int { val xs = [10,20,30,40]\n return xs.first() + xs.last() }", "None", Array()))
    }
  }
}
