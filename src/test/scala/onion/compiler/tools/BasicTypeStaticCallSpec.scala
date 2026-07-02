package onion.compiler.tools

import onion.tools.Shell

/**
 * A basic-type keyword (Long, Int, Double, ...) may be used as a static-call
 * receiver; it maps to its boxed class, so `Long::toString(42L)` calls
 * java.lang.Long.toString. Basic-type keywords are otherwise reserved words, so
 * this was previously a syntax error.
 */
class BasicTypeStaticCallSpec extends AbstractShellSpec {
  describe("basic-type keyword static receiver") {
    it("Long::toString") {
      assert(Shell.Success("42") == shell.run("def main(args: String[]): String { return Long::toString(42L) }", "None", Array()))
    }
    it("Int::parseInt maps to Integer") {
      assert(Shell.Success(7) == shell.run("def main(args: String[]): Int { return Int::parseInt(\"7\") }", "None", Array()))
    }
    it("Long::MAX_VALUE static field") {
      assert(Shell.Success(9223372036854775807L) == shell.run("def main(args: String[]): Long { return Long::MAX_VALUE }", "None", Array()))
    }
    it("does not disturb basic types in type positions") {
      val r = shell.run(
        """
          |def main(args: String[]): Int {
          |  val xs = new Int[3]
          |  xs[0] = 5
          |  val y = (5 as Long)
          |  return xs[0] + (y as Int)
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(10) == r)
    }
  }
}
