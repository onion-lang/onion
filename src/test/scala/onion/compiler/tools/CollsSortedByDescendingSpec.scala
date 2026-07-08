package onion.compiler.tools

import onion.tools.Shell

/**
 * Colls::sortedByDescending sorts a list by a key selector in descending order —
 * the common "top-N by field" operation that otherwise needs a negated key with
 * plain sortedBy. Registered as a builtin extension, so it chains off a list.
 */
class CollsSortedByDescendingSpec extends AbstractShellSpec {
  it("sorts by a key descending as a method chain") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   val xs: List[Int] = [3, 1, 4, 1, 5, 9, 2, 6]
        |   return "" + xs.sortedByDescending { x => x }
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("[9, 6, 5, 4, 3, 2, 1, 1]") == result)
  }
}
