package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic call checks the type arguments it infers against bounds that name
 * another type parameter of the same method (`[B, T extends B]`), after every
 * type argument is known (#2325). Before, `T extends B` was checked only
 * against `B`'s own bound, so a call that broke it compiled and then threw a
 * ClassCastException.
 */
class GenericCallBoundCheckSpec extends AbstractShellSpec {
  describe("a call whose inferred type arguments break a chained bound") {
    it("is rejected when the expected return type fixes B (issue repro)") {
      val r = shell.run(
        """def g[B, T extends B](v: T): B = (v as B)
          |def main(args: String[]): Int { val s: String = g(Integer::valueOf(1)); IO::println(s); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is rejected when two arguments fix B and T to unrelated types") {
      val r = shell.run(
        """def pick[B, T extends B](b: B, t: T): B = b
          |def main(args: String[]): Int { IO::println(pick("a", Integer::valueOf(1))); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is rejected for an instance method too") {
      val r = shell.run(
        """class K {
          |public:
          |  def this { }
          |  def pick[B, T extends B](b: B, t: T): B = b
          |}
          |def main(args: String[]): Int { IO::println(new K().pick("a", Integer::valueOf(1))); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
  describe("a call whose inferred type arguments satisfy a chained bound") {
    it("compiles and runs when T is B") {
      val r = shell.run(
        """def g[B, T extends B](v: T): B = (v as B)
          |def main(args: String[]): Int { val s: String = g("abc"); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
    it("compiles and runs when T is a subtype of B") {
      val r = shell.run(
        """def pick[B, T extends B](b: B, t: T): B = t
          |def main(args: String[]): Int { val n: Number = pick(Integer::valueOf(1) as Number, Integer::valueOf(7)); return n.intValue() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("compiles and runs when B is left to its bound") {
      val r = shell.run(
        """def g[B, T extends B](v: T): B = (v as B)
          |def main(args: String[]): Int { IO::println(g(Integer::valueOf(4))); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("leaves an F-bounded parameter alone") {
      val r = shell.run(
        """def h[T extends Comparable[T]](v: T): T = v
          |def main(args: String[]): Int { return h("xy").length() + h(3) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
  }
  describe("a call where nothing fixes the bounded parameter (panel blocks on #2434)") {
    it("compiles when T appears in no parameter") {
      val r = shell.run(
        """def mk[B, T extends B](b: B): B = b
          |def main(args: String[]): Int { val s: String = mk("x"); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("compiles when T is fixed only by an empty list literal") {
      val r = shell.run(
        """def firstOr[B, T extends B](xs: List[T], d: B): B = if xs.size > 0 { xs[0] } else { d }
          |def main(args: String[]): Int { val s: String = firstOr([], "d2"); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
    it("compiles when T is fixed only by a null argument") {
      val r = shell.run(
        """def pick[B, T extends B](b: B, t: T?): B = b
          |def main(args: String[]): Int { val s: String = pick("a", null); return s.length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("compiles for a null argument under a nullable chained bound") {
      val r = shell.run(
        """def g[B, T extends B](v: T?): B? = null
          |def wid[B, T extends B?](v: T): B? = null
          |def main(args: String[]): Int { val s: String? = g(null); val u: Object? = wid(null); return if s == null && u == null { 4 } else { 0 } }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(4) == r)
    }
  }
}
