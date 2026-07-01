package onion.compiler.tools

import onion.tools.Shell

/**
 * Generic type arguments are invariant (erasure-based generics, no variance):
 * Box[Dog] is NOT a Box[Animal]. The unsound covariant assignment previously
 * compiled and caused a heap-pollution ClassCastException at run time.
 */
class GenericInvarianceSpec extends AbstractShellSpec {
  private val decls =
    """
      |class Animal { public: def this {} }
      |class Dog : Animal { public: def this {} }
      |class Cat : Animal { public: def this {} }
      |class Box[T] {
      |  var value: T
      |public:
      |  def this(v: T) { value = v }
      |  def get(): T { return value }
      |  def set(v: T): void { value = v }
      |}
      |""".stripMargin

  private def fails(body: String): Boolean =
    shell.run(decls + body, "None", Array()) == Shell.Failure(-1)

  describe("generic invariance") {
    it("rejects assigning Box[Dog] to Box[Animal]") {
      assert(fails("val bd: Box[Dog] = new Box[Dog](new Dog())\nval ba: Box[Animal] = bd\n"))
    }
    it("rejects assigning new Box[Dog] directly to Box[Animal]") {
      assert(fails("val ba: Box[Animal] = new Box[Dog](new Dog())\n"))
    }
    it("allows same-parameterization assignment") {
      val r = shell.run(decls +
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val bd: Box[Dog] = new Box[Dog](new Dog())
          |    val bd2: Box[Dog] = bd
          |    return bd2.get() != null
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r)
    }
    it("allows List[String] to List[String]") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[String] = ["a", "b"]
          |    val ys: List[String] = xs
          |    return ys.size
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }
}
