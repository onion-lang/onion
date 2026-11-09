package onion.compiler.tools

import onion.tools.Shell

class IsPrimitiveWrapperSpec extends AbstractShellSpec {
  private def run(body: String) =
    shell.run(
      s"""
         |class IsPrim {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("`is` against a primitive type name") {
    it("checks the wrapper class of an Object value") {
      val r = run(
        """    val a: Object = 1L
          |    val b: Object = 2
          |    val c: Object = 1.5
          |    val d: Object = true
          |    val e: Object = "s"
          |    return "" + (a is Long) + (a is Int) + (b is Int) + (c is Double) + (d is Boolean) + (e is Long)""".stripMargin)
      assert(Shell.Success("truefalsetruetruetruefalse") == r)
    }

    it("is true for a primitive-typed operand of the same type") {
      val r = run(
        """    val n: Int = 3
          |    return "" + (n is Int) + (n is Long)""".stripMargin)
      assert(Shell.Success("truefalse") == r)
    }
  }

  describe("`case n is Long:` type pattern (#2721)") {
    it("matches the wrapper and binds the unboxed value") {
      val r = run(
        """    val o: Object = 5L
          |    val p: Object = 2
          |    val q: Number = 2.5
          |    val a = select o { case n is Long: "long " + (n + 1L); else: "other" }
          |    val b = select p { case n is Long: "long"; case i is Int: "int " + (i * 2); else: "other" }
          |    val c = select q { case d is Double: "double " + (d + 0.5); else: "other" }
          |    val e = select "s" as Object { case n is Long: "long"; else: "other" }
          |    return a + "," + b + "," + c + "," + e""".stripMargin)
      assert(Shell.Success("long 6,int 4,double 3.0,other") == r)
    }
  }
}
