package onion.compiler.tools

import onion.tools.Shell

/**
 * #2025: an explicit `import ... as <alias>` must shadow a same-named class
 * that one of Onion's own default on-demand imports (java.lang.*, java.util.*,
 * javax.swing.*, ...) would otherwise resolve the alias to. `JList` collides
 * with the real `javax.swing.JList`, which is why the reported repro used
 * that exact alias; the root cause is resolution order, not anything specific
 * to `java.util.List`.
 */
class ImportAliasPriorityOverDefaultSpec extends AbstractShellSpec {
  describe("An explicit import alias colliding with a default on-demand import") {
    it("still resolves to the aliased class, not the default-imported one") {
      val result = shell.run(
        """
          | import {
          |   java.util.ArrayList
          |   java.util.List as JList;
          | }
          | class UseAliasedList {
          | public:
          |   static def main(args: String[]): Int {
          |     val l: ArrayList[String] = new ArrayList[String]()
          |     l.add("x")
          |     val l2: JList[String] = l
          |     return l2.size()
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("keeps working with no alias (control)") {
      val result = shell.run(
        """
          | import {
          |   java.util.ArrayList
          |   java.util.List;
          | }
          | class UseUnaliasedList {
          | public:
          |   static def main(args: String[]): Int {
          |     val l: ArrayList[String] = new ArrayList[String]()
          |     l.add("x")
          |     val l2: List[String] = l
          |     return l2.size()
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }
}
