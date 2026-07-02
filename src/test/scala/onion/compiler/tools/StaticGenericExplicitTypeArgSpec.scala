package onion.compiler.tools

import onion.tools.Shell

/**
 * A static generic method called with an explicit primitive type argument boxes
 * the primitive argument, e.g. `Util::identity[Int](99)`.
 */
class StaticGenericExplicitTypeArgSpec extends AbstractShellSpec {
  private val util = "class Util {\npublic:\n  static def identity[T](x: T): T { return x }\n}\n"
  describe("static generic method with an explicit primitive type argument") {
    it("boxes an Int argument") {
      assert(Shell.Success(99) == shell.run(util + "def main(args: String[]): Int { return Util::identity[Int](99) }", "None", Array()))
    }
    it("boxes a Long argument") {
      assert(Shell.Success(5L) == shell.run(util + "def main(args: String[]): Long { return Util::identity[Long](5L) }", "None", Array()))
    }
    it("still accepts a reference type argument and inference") {
      assert(Shell.Success("ok") == shell.run(util + "def main(args: String[]): String { return Util::identity[String](\"ok\") }", "None", Array()))
      assert(Shell.Success(7) == shell.run(util + "def main(args: String[]): Int { return Util::identity(7) }", "None", Array()))
    }
  }
}
