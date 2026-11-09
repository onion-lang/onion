package onion.compiler.tools

import onion.tools.Shell

/** A reference-type test on a primitive value tests the boxed value (#2726); it used to be a VerifyError. */
class PrimitiveTypeTestSpec extends AbstractShellSpec {
  private def run(body: String) =
    shell.run(
      s"""
         |import { java.lang.Long as JLong; }
         |record Wrap(v: Int)
         |record P(n: Long, s: String)
         |record Box(w: Wrap)
         |class T {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("is on a primitive operand") {
    it("tests the boxed value") {
      assert(run("val x: Long = 5L\n return \"\" + (x is JLong) + (x is String)") == Shell.Success("truefalse"))
    }
  }

  describe("a type pattern on a primitive scrutinee") {
    it("matches the boxed value and binds it") {
      assert(run("val x: Long = 5L\n return select x { case n is JLong: \"boxed \" + n; else: \"other\" }") == Shell.Success("boxed 5"))
    }
    it("does not match an unrelated reference type") {
      assert(run("val i: Int = 5\n return select i { case s is String: \"str\"; else: \"other\" }") == Shell.Success("other"))
    }
  }

  describe("a type pattern nested in a record pattern, on a primitive component") {
    it("matches the boxed component and binds it") {
      assert(run("val w = new Wrap(5)\n return select w { case Wrap(n is Object): \"obj \" + n; else: \"other\" }") == Shell.Success("obj 5"))
    }
    it("does not match an unrelated reference type") {
      assert(run("val w = new Wrap(5)\n return select w { case Wrap(s is String): \"str \" + s; else: \"other\" }") == Shell.Success("other"))
    }
    it("binds the wrapper type next to another component") {
      assert(run("val p = new P(7L, \"x\")\n return select p { case P(n is JLong, s): \"\" + (n.longValue() + 1L) + s; else: \"other\" }") == Shell.Success("8x"))
    }
    it("works two levels deep, and a record pattern on a primitive is a plain non-match") {
      assert(run("val b = new Box(new Wrap(3))\n return select b { case Box(Wrap(n is Number)): \"num \" + n; else: \"other\" }") == Shell.Success("num 3"))
      assert(run("val w = new Wrap(5)\n return select w { case Wrap(Wrap(n)): \"wrap\"; else: \"other\" }") == Shell.Success("other"))
    }
  }
}
