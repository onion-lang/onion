package onion.compiler.tools

import onion.tools.Shell

/**
 * An untyped lambda passed to a constructor whose later parameters take their defaults is
 * typed against that constructor's parameter, as the same call on a method is (#2919).
 */
class ConstructorLambdaDefaultSpec extends AbstractShellSpec {
  describe("a lambda argument with a defaulted constructor parameter") {
    it("types the lambda against the only candidate") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val r: Int
          |  def this(f: Int -> Int, k: Int = 1) { this.r = f(k) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new A(x -> x * 2).r + "," + new A(x -> x * 2, 5).r
          |  }
          |}
          |""".stripMargin, "ld.on", Array[String]())
      assert(Shell.Success("2,10") == result)
    }
    it("types a lambda that follows another argument") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val r: Int
          |  def this(n: Int, f: Int -> Int, k: Int = 1) { this.r = n + f(k) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "" + new A(10, x -> x * 2).r }
          |}
          |""".stripMargin, "ld2.on", Array[String]())
      assert(Shell.Success("12") == result)
    }
    it("still types the lambda when an unrelated constructor has the same arity") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val r: Int
          |  def this(f: Int -> Int, k: Int = 1) { this.r = f(k) }
          |  def this(s: String) { this.r = s.length() }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new A(x -> x * 2).r + "," + new A("abc").r
          |  }
          |}
          |""".stripMargin, "ld3.on", Array[String]())
      assert(Shell.Success("2,3") == result)
    }
    it("keeps an Object overload with more defaults for a zero-parameter lambda") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val tag: String
          |  def this(f: Int -> Int, k: Int = 1) { tag = "fn" + f(k) }
          |  def this(o: Object, k: Int = 2, j: Int = 3) { tag = "obj" + k }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return new A(() -> 5).tag }
          |}
          |""".stripMargin, "ld4.on", Array[String]())
      assert(Shell.Success("obj2") == result)
    }
    it("keeps an Object overload with more defaults for a typed lambda") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val tag: String
          |  def this(f: (String) -> Int, k: Int = 1) { tag = "fn" + f("ab") }
          |  def this(o: Object, k: Int = 2, j: Int = 3) { tag = "obj" + k + j }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return new A((x: Int) -> x * 10).tag }
          |}
          |""".stripMargin, "ld5.on", Array[String]())
      assert(Shell.Success("obj23") == result)
    }
    it("stays E0052 when an Object constructor takes the same call, so an annotation cannot switch constructors") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  def this(f: Int -> Int, k: Int = 1) { println("fn") }
          |  def this(o: Object) { println("obj") }
          |}
          |new A(x -> x)
          |""".stripMargin, "ld9.on", Array[String]())
      assert(Shell.Failure(-1) == result)
    }
    it("keeps a typed lambda on the Object constructor, as on main") {
      val result = shell.run(
        """
          |class A {
          |public:
          |  val tag: String
          |  def this(f: Int -> Int, k: Int = 1) { tag = "fn" }
          |  def this(o: Object) { tag = "obj" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return new A((x: Int) -> x).tag }
          |}
          |""".stripMargin, "ld10.on", Array[String]())
      assert(Shell.Success("obj") == result)
    }
  }
}
