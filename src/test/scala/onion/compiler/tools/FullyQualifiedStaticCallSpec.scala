package onion.compiler.tools

import onion.tools.Shell

/**
 * A static call/member on a fully-qualified (dotted) class name, e.g.
 * `java.lang.Math::max(...)`, works without an import. (Basic-type keyword
 * receivers such as `Long::` are a separate matter — Long/Int/etc. are keywords,
 * not identifiers.)
 */
class FullyQualifiedStaticCallSpec extends AbstractShellSpec {
  describe("fully-qualified static call") {
    it("calls a static method on a dotted class name") {
      assert(Shell.Success(7) == shell.run("def main(args: String[]): Int { return java.lang.Math::max(3, 7) }", "None", Array()))
    }
    it("calls a static method returning a value") {
      assert(Shell.Success("255") == shell.run("def main(args: String[]): String { return java.lang.Integer::toString(255) }", "None", Array()))
    }
    it("reads a static field on a dotted class name") {
      assert(Shell.Success(2147483647) == shell.run("def main(args: String[]): Int { return java.lang.Integer::MAX_VALUE }", "None", Array()))
    }
    it("does not disturb member access or imported static calls") {
      assert(Shell.Success(3) == shell.run("def main(args: String[]): Int { val s = \"abc\"\n return s.length() }", "None", Array()))
      assert(Shell.Success(7) == shell.run("def main(args: String[]): Int { return Math::max(3, 7) }", "None", Array()))
    }
  }
}
