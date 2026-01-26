package onion.compiler.tools

import onion.tools.Shell

class ImportSpec extends AbstractShellSpec {
  describe("Import a class") {
    it("import java.util.*") {
      val result = shell.run(
        """
          | import {
          |   java.util.*
          | }
          | class Increment {
          | public:
          |   static def main(args: String[]): Int {
          |     val xs: ArrayList = new ArrayList()
          |     xs.add(new Integer(2))
          |     return (xs.get(0) as Integer).intValue()
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(2) == result)
    }
  }
}
