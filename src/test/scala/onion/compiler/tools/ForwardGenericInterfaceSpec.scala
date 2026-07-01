package onion.compiler.tools

import onion.tools.Shell

/**
 * `forward` delegation over a parameterized generic interface (including
 * java.util collection interfaces) now compiles and runs. The underlying fix is
 * in bridge-method generation: a method declared at several levels of a generic
 * hierarchy (e.g. addLast on both List and SequencedCollection) must yield a
 * single bridge, not a duplicate (previously a ClassFormatError) — which also
 * affected hand-written classes implementing such hierarchies.
 */
class ForwardGenericInterfaceSpec extends AbstractShellSpec {
  describe("bridge dedup for multi-level generic hierarchies") {
    it("compiles a hand-written class implementing a method declared at two interface levels") {
      val r = shell.run(
        """
          |interface A[T] {
          |  def f(x: T): String
          |}
          |interface B[T] <: A[T] {
          |  def f(x: T): String
          |}
          |class Real <: B[String] {
          |public:
          |  def this {}
          |  def f(x: String): String { return "f:" + x }
          |  static def main(args: String[]): String { return new Real().f("hi") }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("f:hi") == r)
    }
  }

  describe("forward over a generic interface") {
    it("single-level user-defined interface") {
      val r = shell.run(
        """
          |interface Box[T] {
          |  def get(): T
          |}
          |class RealBox <: Box[String] {
          |public:
          |  def this {}
          |  def get(): String { return "hello" }
          |}
          |class Fwd <: Box[String] {
          |  forward val b: Box[String]
          |public:
          |  def this(x: Box[String]) { b = x }
          |}
          |def main(args: String[]): String { return new Fwd(new RealBox()).get() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("hello") == r)
    }

    it("primitive type argument (Container[Int])") {
      val r = shell.run(
        """
          |interface Container[T] {
          |  def get(): T
          |  def put(x: T): void
          |}
          |class IntBox <: Container[Int] {
          |  var v: Int
          |public:
          |  def this { v = 0 }
          |  def get(): Int { return v }
          |  def put(x: Int): void { v = x }
          |}
          |class Fwd <: Container[Int] {
          |  forward val c: Container[Int]
          |public:
          |  def this(x: Container[Int]) { c = x }
          |}
          |def main(args: String[]): Int {
          |  val f = new Fwd(new IntBox())
          |  f.put(99)
          |  return f.get()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(99) == r)
    }

    it("java.util.List[String] (the issue example), usable as List[String]") {
      val r = shell.run(
        """
          |class MyList <: List[String] {
          |  forward val backing: List[String]
          |public:
          |  def this(xs: List[String]) { backing = xs }
          |}
          |def main(args: String[]): Int {
          |  val m: List[String] = new MyList(["a", "b"])
          |  m.add("c")
          |  var n: Int = 0
          |  foreach s: String in m { n = n + s.length() }
          |  return m.size() + n
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(6) == r)  // size 3 + (1+1+1) chars
    }

    it("java.util.Map[String, Int]") {
      val r = shell.run(
        """
          |class MyMap <: Map[String, Int] {
          |  forward val backing: Map[String, Int]
          |public:
          |  def this(m: Map[String, Int]) { backing = m }
          |}
          |def main(args: String[]): Int {
          |  val mm: Map[String, Int] = new MyMap(["a": 1, "b": 2])
          |  return mm.size()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }

    it("still works for a non-generic interface") {
      val r = shell.run(
        """
          |interface Greeter {
          |  def greet(): String
          |}
          |class Polite <: Greeter {
          |public:
          |  def this {}
          |  def greet(): String { return "Hi" }
          |}
          |class D <: Greeter {
          |  forward val g: Greeter
          |public:
          |  def this(x: Greeter) { g = x }
          |}
          |def main(args: String[]): String { return new D(new Polite()).greet() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("Hi") == r)
    }
  }
}
