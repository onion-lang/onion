package onion.compiler.tools

import onion.tools.Shell

/**
 * A dotted, fully-qualified name may be used as a type argument in type
 * annotations (val/param/return/field) and in `new` with a constructor `()`.
 */
class FullyQualifiedTypeArgumentSpec extends AbstractShellSpec {
  describe("fully-qualified type arguments") {
    it("accepts an FQN type argument in a val annotation and new(...)") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val xs: java.util.List[java.lang.Integer] = new java.util.ArrayList[java.lang.Integer]()
          |  xs.add(5)
          |  return xs.get(0)
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
    it("accepts an FQN type argument in a parameter annotation") {
      val r = shell.run(
        """def size(m: java.util.Map[java.lang.String, java.lang.Integer]): Int { return m.size() }
          |def main(args: String[]): Int { return size(new java.util.HashMap[java.lang.String, java.lang.Integer]()) }""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("does not break array-size expressions with a field access") {
      val r = shell.run(
        """class C { public: var size: Int
          |  def this(n: Int) { size = n } }
          |def main(args: String[]): Int {
          |  val c = new C(3)
          |  val a = new Int[c.size]
          |  return a.length
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
  }
}
