package onion.compiler.tools

import onion.tools.Shell

/**
 * An F-bounded parameter (`A extends Animal[A]`) used to fill another F-bound: in a
 * second declaration's bounds, and as the inferred argument of a generic call (#2338).
 */
class FBoundDeclarationSpec extends AbstractShellSpec {
  private val animal =
    """abstract class Animal[A extends Animal[A]] {
      |public:
      |  abstract def me(): A
      |}
      |""".stripMargin

  describe("an F-bounded parameter in another declaration's bounds") {
    it("is accepted where it meets the bound") {
      val r = shell.run(
        animal +
          """class Holder[A extends Animal[A], C extends Animal[C]] {
            |}
            |class Dog extends Animal[Dog] { public: def me(): Dog = this }
            |def main(args: String[]): Int { val h = new Holder[Dog, Dog](); return 1 }
            |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("is accepted in a generic method's bounds inside the F-bounded class") {
      val r = shell.run(
        """abstract class Animal[A extends Animal[A]] {
          |public:
          |  abstract def me(): A
          |  def other[B extends Animal[B]](b: B): B = b.me()
          |}
          |class Dog extends Animal[Dog] { public: def me(): Dog = this }
          |def main(args: String[]): Int { return if new Dog().other(new Dog()) != null { 1 } else { 0 } }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("is still rejected when the parameter has no such bound") {
      val r = shell.run(
        animal +
          """class Holder[C, D extends Animal[C]] {
            |}
            |def main(args: String[]): Int { return 0 }
            |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("still rejects a type argument that does not meet the bound") {
      val r = shell.run(
        animal +
          """class Holder[C extends Animal[C]] {
            |}
            |def main(args: String[]): Int { val h = new Holder[String](); return 0 }
            |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }

  describe("an F-bounded generic method called with an inferred type argument") {
    it("accepts a variable with the same F-bound") {
      val r = shell.run(
        """class G {
          |public:
          |  static def f[T extends Comparable[T]](x: List[T]): Int = x.size
          |  static def g[U extends Comparable[U]](x: List[U]): Int = G::f(x)
          |  static def h[T extends Comparable[T]](x: List[T]): Int = G::f(x)
          |}
          |def main(args: String[]): Int { return G::g(["a"]) + G::h(["a", "b"]) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
    it("accepts a concrete type that meets the bound") {
      val r = shell.run(
        """import { java.lang.Integer as JI }
          |class G {
          |public:
          |  static def f[T extends Comparable[T]](x: List[T]): Int = x.size
          |}
          |def main(args: String[]): Int { val xs: List[JI] = [1, 2]; return G::f(xs) + G::f[String](["q"]) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
    it("still rejects a variable bounded by a different Comparable") {
      val r = shell.run(
        """class G {
          |public:
          |  static def f[T extends Comparable[T]](x: List[T]): Int = x.size
          |  static def k[U extends Comparable[String]](x: List[U]): Int = G::f(x)
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("still rejects a type that is not Comparable to itself") {
      val r = shell.run(
        """class G {
          |public:
          |  static def f[T extends Comparable[T]](x: List[T]): Int = x.size
          |}
          |def main(args: String[]): Int { val os: List[Object] = ["a"]; return G::f(os) }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }

  describe("derive!(Json) on an F-bounded generic record") {
    it("compiles and serializes") {
      val r = shell.run(
        """record B[T extends Comparable[T]](n: Int) derive!(Json)
          |def main(args: String[]): Int { return if B::toJson(new B[String](1)) == "{\"n\":1}" { 1 } else { 0 } }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
}
