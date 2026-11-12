package onion.compiler.tools

import onion.tools.Shell

/**
 * #2677: `new C[T](xs)` with `def this(xs: List[T])` inside a generic class read `T` through
 * its bound, so a `List[Number]` (or, with an unbounded `T`, a `List[String]`) passed the
 * constructor's argument check, and a `Double` later sat in a `List[Integer]`
 * (ClassCastException at the first read). A method call `f(xs)` already rejected it (#2676).
 */
class ConstructorTypeVariableArgumentSpec extends AbstractShellSpec {
  private def run(source: String): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array()) } }
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }

  private val boundedC =
    """class C[T extends Number] {
      |public:
      |  val xs: List[T]
      |  def this(xs: List[T]) { this.xs = xs }
      |}
      |""".stripMargin

  describe("a constructor slot typed by a class type variable") {
    it("rejects a List[Number] for List[T] (T extends Number) at compile time") {
      val src = boundedC +
        """class D[T extends Number] {
          |public:
          |  def this {}
          |  def go(xs: List[Number]): C[T] = new C[T](xs)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: C[Integer] = new D[Integer]().go([1.5])
          |    val i: Integer = c.xs[0]
          |    return "ran " + i
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Failure(-1), out)
      assert(!out.contains("ClassCastException") && !out.contains("cannot be cast"), out)
    }

    it("rejects a List[String] for List[T] with an unbounded T") {
      val src =
        """class Box[T] {
          |public:
          |  val xs: List[T]
          |  def this(xs: List[T], d: Int) { this.xs = xs }
          |}
          |class G[T] {
          |public:
          |  def this {}
          |  def go(xs: List[String]): Box[T] = new Box[T](xs, 1)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Box[Integer] = new G[Integer]().go(["a"])
          |    val i: Integer = b.xs[0]
          |    return "ran " + i
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Failure(-1), out)
    }

    it("takes the Object overload when only it fits, as Java does") {
      val src =
        """class C[T extends Number] {
          |public:
          |  var which: String = ""
          |  def this(xs: List[T]) { which = "list" }
          |  def this(o: Object) { which = "obj" }
          |}
          |class D[T extends Number] {
          |public:
          |  def this {}
          |  def go(xs: List[Number]): String = new C[T](xs).which
          |  def same(xs: List[T]): String = new C[T](xs).which
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d = new D[Integer]()
          |    return d.go([1.5]) + "," + d.same([1])
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Success("obj,list"), out)
    }

    it("still accepts a List[T], an ArrayList[T] and concrete type arguments") {
      val src = "import { java.util.ArrayList }\n" + boundedC +
        """class D[T extends Number] {
          |public:
          |  def this {}
          |  def same(xs: List[T]): C[T] = new C[T](xs)
          |  def sub(xs: ArrayList[T]): C[T] = new C[T](xs)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new D[Integer]().same([1, 2])
          |    val al = new ArrayList[Integer]()
          |    al.add(3)
          |    val b = new D[Integer]().sub(al)
          |    val c = new C[Integer]([4])
          |    return "" + a.xs.size + b.xs[0] + c.xs[0]
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Success("234"), out)
    }
  }
}
