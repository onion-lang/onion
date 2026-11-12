package onion.compiler.tools

import onion.tools.Shell

/**
 * A type argument is checked against its bound with the bound's type arguments, not by
 * erasure: a `Cat` that is an `Animal[Dog]` does not meet `C extends Animal[C]`, and an
 * `Integer` does not meet `T extends Comparable[String]` (#2780). Before, such calls
 * compiled and then threw ClassCastException.
 */
class AppliedBoundCheckSpec extends AbstractShellSpec {
  private val animals =
    """interface Animal[A] { def me(): A }
      |class Dog conforms Animal[Dog] { public: def me(): Dog = this }
      |class Cat conforms Animal[Dog] { public: def me(): Dog = new Dog() }
      |class G { public: static def f[C extends Animal[C]](c: C): C = c.me() }
      |""".stripMargin

  private val cmp =
    """import { java.lang.Integer as JInteger }
      |class K { public: static def m[T extends Comparable[String]](x: T): Int = x.compareTo("x") }
      |""".stripMargin

  describe("an inferred type argument") {
    it("is rejected when the class meets the F-bound for another type") {
      val r = shell.run(animals + "def main(args: String[]): Int { val c: Cat = G::f(new Cat()); return 0 }\n", "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is accepted when the class meets the F-bound for itself") {
      val r = shell.run(animals + "def main(args: String[]): Int { val d: Dog = G::f(new Dog()); return if d != null { 1 } else { 0 } }\n", "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("is rejected when a boxed class's own view contradicts the bound") {
      val r = shell.run(cmp + "def main(args: String[]): Int { val b: JInteger = 5; return K::m(b) }\n", "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is rejected for a primitive argument whose box contradicts the bound") {
      val r = shell.run(cmp + "def main(args: String[]): Int { return K::m(5) }\n", "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is accepted when the class's own view matches the bound") {
      val r = shell.run(cmp + "def main(args: String[]): Int { return if K::m(\"x\") == 0 { 1 } else { 0 } }\n", "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("keeps the Comparable F-bound working for String and Integer") {
      val r = shell.run(
        """class M { public: static def mx[T extends Comparable[T]](a: T, b: T): T = if a.compareTo(b) >= 0 { a } else { b } }
          |def main(args: String[]): Int { return if M::mx("a", "b") == "b" { M::mx(1, 2) } else { 0 } }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }

  describe("an explicit type argument in a type position") {
    it("is rejected when a class's own view contradicts the bound") {
      val r = shell.run(
        cmp + "class H[T extends Comparable[String]] { public: def this {} }\ndef main(args: String[]): Int { val h = new H[JInteger](); return 0 }\n",
        "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is rejected when the class meets the F-bound for another type") {
      val r = shell.run(
        animals + "class P[C extends Animal[C]] { public: def this {} }\ndef main(args: String[]): Int { val p = new P[Cat](); return 0 }\n",
        "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("is accepted when the class meets the F-bound for itself") {
      val r = shell.run(
        animals + "class P[C extends Animal[C]] { public: def this {} }\ndef main(args: String[]): Int { val p = new P[Dog](); return 1 }\n",
        "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
}
