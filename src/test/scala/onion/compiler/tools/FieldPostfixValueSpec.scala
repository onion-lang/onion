package onion.compiler.tools

import onion.tools.Shell

class FieldPostfixValueSpec extends AbstractShellSpec {
  describe("postfix ++/-- on an instance field") {
    it("yields the old value, as for a local, a static field and an array element") {
      val result = shell.run(
        """
          | class K {
          | public:
          |   var n: Int
          |   def this { n = 5 }
          |   def next: Int { return n++ }
          |   def prev: Int { return this.n-- }
          |   static def main(args: String[]): Int {
          |     val k = new K()
          |     val a = k.n++        // 5, n = 6
          |     val b = k.n--        // 6, n = 5
          |     val c = k.next()     // 5, n = 6
          |     val d = k.prev()     // 6, n = 5
          |     return a * 10000 + b * 1000 + c * 100 + d * 10 + k.n
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(56565) == result)
    }
    it("evaluates the receiver once and keeps the long type") {
      val result = shell.run(
        """
          | class C {
          | public:
          |   var v: Long
          |   def this { v = 7L }
          | }
          | class T {
          | public:
          |   static var made: Int
          |   static def mk(c: C): C { made++; return c }
          |   static def main(args: String[]): Int {
          |     val c = new C()
          |     val old: Long = mk(c).v++
          |     return (old as Int) * 100 + (c.v as Int) * 10 + made
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(781) == result)
    }
  }
}
