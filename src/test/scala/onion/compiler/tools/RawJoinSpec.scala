package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #3255 (and #3247): branches of one generic class with different type
 * arguments joined as the raw class, which fits every instantiation. A join that
 * reaches a call argument, a non-generic lambda result or an untyped local is no
 * longer an `Option[Int]`.
 */
class RawJoinSpec extends AbstractShellSpec {

  private val header =
    "import { onion.Option; java.util.List; }\n" +
      "val tail: Option[Int] = Option::none()\n" +
      "val other: Option[String] = Option::some(\"str\")\n"

  private def run(body: String, name: String) = shell.run(header + body, name, Array())

  describe("a join of Option[String] and Option[Int] is not an Option[Int] (#3255)") {
    it("a lambda for a non-generic function parameter") {
      val r = run(
        """def chk(f: () -> Option[Int]): Option[Int] = f()
          |val c: Option[Int] = chk { if 1 < 2 { other } else { tail } }
          |def main(args: String[]): String = "" + (c.get() + 1)
          |""".stripMargin, "RawJoinLambda.on")
      assert(Shell.Failure(-1) == r)
    }
    it("an untyped local") {
      val r = run(
        """val x = if 1 < 2 { other } else { tail }
          |val c: Option[Int] = x
          |def main(args: String[]): String = "" + (c.get() + 1)
          |""".stripMargin, "RawJoinLocal.on")
      assert(Shell.Failure(-1) == r)
    }
    it("a call argument, for a function, a method and a constructor (#3247)") {
      val fn = run(
        """def f(x: Option[Int]): Option[Int] = x
          |def main(args: String[]): String = "" + f(if 1 < 2 { other } else { tail }).get()
          |""".stripMargin, "RawJoinArgFn.on")
      assert(Shell.Failure(-1) == fn)
      val st = run(
        """class K { public: static def g(x: Option[Int]): Option[Int] = x }
          |def main(args: String[]): String = "" + K::g(if 1 < 2 { other } else { tail }).get()
          |""".stripMargin, "RawJoinArgStatic.on")
      assert(Shell.Failure(-1) == st)
      val ctor = run(
        """class W(x: Option[Int]) { public: def get: Option[Int] = x }
          |def main(args: String[]): String = "" + new W(if 1 < 2 { other } else { tail }).get
          |""".stripMargin, "RawJoinArgCtor.on")
      assert(Shell.Failure(-1) == ctor)
    }
    it("a join still goes to Object, to a raw value and through a method of the class") {
      val r = run(
        """def show(o: Object): String = "" + o
          |val x = if 1 < 2 { other } else { tail }
          |val o: Object = x
          |val same: Option[Int] = if 1 > 2 { Option::some(4) } else { tail }
          |def main(args: String[]): String = show(x) + " " + o + " " + x.isDefined() + " " + same
          |""".stripMargin, "RawJoinOk.on")
      assert(Shell.Success("Some[value=str] Some[value=str] true None[]") == r)
    }
  }
}
