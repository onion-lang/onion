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
    it("PrintStream.println(75) prints the number, not K") {
      assert(Shell.Success(5) == shell.run(
        """
          |def main(args: String[]): Int {
          |  val bytes = new java.io.ByteArrayOutputStream()
          |  val out = new java.io.PrintStream(bytes)
          |  out.println(75)
          |  out.flush()
          |  return if bytes.toString().trim() == "75" { 5 } else { 0 }
          |}
        """.stripMargin, "None", Array()))
    }
    it("prefers widening to Long over narrowing to Char for an Onion overload") {
      assert(Shell.Success(2) == shell.run(
        """
          |def g(c: Char): Int { return 1 }
          |def g(l: Long): Int { return 2 }
          |def main(args: String[]): Int { return g(65) }
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
