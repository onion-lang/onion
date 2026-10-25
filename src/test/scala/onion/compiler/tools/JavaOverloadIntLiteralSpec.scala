package onion.compiler.tools

import onion.tools.Shell

/** An int literal passed to an overloaded Java method takes the int overload, not char (#2425). */
class JavaOverloadIntLiteralSpec extends AbstractShellSpec {
  describe("int literal against Java overloads") {
    it("StringBuilder.append(75) appends the number, not a char") {
      assert(Shell.Success(4) == shell.run(
        """
          |def main(args: String[]): Int {
          |  val sb = new StringBuilder()
          |  sb.append(75)
          |  sb.insert(0, 66)
          |  return if sb.toString() == "6675" { 4 } else { 0 }
          |}
        """.stripMargin, "None", Array()))
    }
    it("still narrows a literal when only a Byte overload exists") {
      assert(Shell.Success(100) == shell.run(
        """
          |def takesByte(b: Byte): Int { return b as Int }
          |def main(args: String[]): Int { return takesByte(100) }
        """.stripMargin, "None", Array()))
    }
  }
}
