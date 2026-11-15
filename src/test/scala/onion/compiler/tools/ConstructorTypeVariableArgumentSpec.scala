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

    it("rejects a List[Number] passed by name (`xs = xs`)") {
      val src = boundedC +
        """class D[T extends Number] {
          |public:
          |  def this {}
          |  def go(xs: List[Number]): C[T] = new C[T](xs = xs)
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
      assert(out.contains("E0021"), out)
    }

    it("rejects a List[Number] for a constructor that fills a default parameter") {
      val src =
        """class C[T extends Number] {
          |public:
          |  val xs: List[T]
          |  def this(xs: List[T], d: Int = 1) { this.xs = xs }
          |}
          |class D[T extends Number] {
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
      assert(out.contains("E0021"), out)
    }

    it("still accepts a List[T] by name and through a default parameter") {
      val src =
        """class C[T extends Number] {
          |public:
          |  val xs: List[T]
          |  val d: Int
          |  def this(xs: List[T], d: Int = 1) { this.xs = xs; this.d = d }
          |}
          |class D[T extends Number] {
          |public:
          |  def this {}
          |  def named(xs: List[T]): C[T] = new C[T](xs = xs)
          |  def dflt(xs: List[T]): C[T] = new C[T](xs)
          |  def empty(): C[T] = new C[T](xs = [], d = 5)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d = new D[Integer]()
          |    val a = d.named([7])
          |    val b = d.dflt([8, 9])
          |    val e = d.empty()
          |    return "" + a.xs[0] + a.d + b.xs.size + b.d + e.xs.size + e.d
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Success("712105"), out)
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

  // #2840: a primitive was left alone by the slot check, so `new Box[T](i)` with `i: Int`
  // boxed to an Integer into a `T` slot, and a `Box[String]` held it.
  describe("a primitive argument into a constructor slot typed by a class type variable") {
    val box =
      """class Box[T] {
        |public:
        |  val v: T?
        |  def this(v: T?) { this.v = v }
        |}
        |class Bare[T] {
        |public:
        |  val v: T
        |  def this(v: T) { this.v = v }
        |}
        |""".stripMargin

    it("rejects an Int into a bare T slot at compile time") {
      val src = box +
        """class G[T] {
          |public:
          |  def this {}
          |  def wrapInt(i: Int): Bare[T] = new Bare[T](i)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = new G[String]().wrapInt(3).v
          |    return "ran " + s
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Failure(-1), out)
      assert(!out.contains("ClassCastException") && !out.contains("cannot be cast"), out)
    }

    it("rejects a Double literal into a T? slot at compile time") {
      val src = box +
        """class G[T] {
          |public:
          |  def this {}
          |  def wrap(): Box[T] = new Box[T](1.5)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String? = new G[String]().wrap().v
          |    return "ran " + s
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Failure(-1), out)
      assert(!out.contains("ClassCastException") && !out.contains("cannot be cast"), out)
    }

    it("still boxes a primitive into a concrete or inferred type argument") {
      val src = box +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Bare[Integer](3)
          |    val b = new Bare(4)
          |    val c: Box[Integer] = new Box[Integer](5)
          |    val d = new Box[Object](6)
          |    return "" + a.v + b.v + c.v + d.v
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Success("3456"), out)
    }

    val doors =
      """class Dflt[T] {
        |public:
        |  val v: T
        |  def this(v: T, d: Int = 1) { this.v = v }
        |}
        |class Va[T] {
        |public:
        |  val n: Int
        |  def this(vs: T...) { this.n = vs.length }
        |}
        |""".stripMargin

    Seq(
      "by name" -> "def make(i: Int): Dflt[T] = new Dflt[T](v = i)",
      "through a default parameter" -> "def make(i: Int): Dflt[T] = new Dflt[T](i)",
      "into a T... vararg" -> "def make(i: Int): Va[T] = new Va[T](i, i)"
    ).foreach { case (door, method) =>
      it(s"rejects an Int into a T slot $door") {
        val src = doors +
          s"""class G[T] {
             |public:
             |  def this {}
             |  $method
             |}
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    val g = new G[String]().make(3)
             |    return "ran"
             |  }
             |}
             |""".stripMargin
        val (result, out) = run(src)
        assert(result == Shell.Failure(-1), out)
      }
    }

    it("still accepts a T by name, through a default and into a vararg, and an Int by name into Dflt[Integer]") {
      val src = doors +
        """class G[T] {
          |public:
          |  def this {}
          |  def a(t: T): Dflt[T] = new Dflt[T](v = t)
          |  def b(t: T): Dflt[T] = new Dflt[T](t)
          |  def c(t: T): Va[T] = new Va[T](t, t)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val g = new G[String]()
          |    val r = new Dflt[Integer](v = 7)
          |    val w = new Va[Integer](1, 2, 3)
          |    return g.a("x").v + g.b("y").v + g.c("z").n + r.v + w.n
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Success("xy273"), out)
    }
  }

  describe("a superclass constructor slot typed by a type variable (#2840)") {
    def failing(name: String, parent: String, sub: String): Unit =
      it(name) {
        val src = parent + sub +
          """class Test {
            |public:
            |  static def main(args: String[]): String { return "ran" }
            |}
            |""".stripMargin
        val (result, out) = run(src)
        assert(result == Shell.Failure(-1), out)
        assert(out.contains("E0021"), out)
      }

    failing("rejects a List[Number] for extends C[T](ys) with T extends Number", boundedC,
      """class S[T extends Number](ys: List[Number]) extends C[T](ys)
        |""".stripMargin)

    failing("rejects a primitive for extends Box[T](i) with an unbounded T",
      """class Box[T] {
        |public:
        |  val v: T
        |  def this(v: T) { this.v = v }
        |}
        |""".stripMargin,
      """class S[T](i: Int) extends Box[T](i)
        |""".stripMargin)

    it("still accepts the parent's own T and concrete type arguments") {
      val src =
        """class Box[T] {
          |public:
          |  val v: T
          |  def this(v: T) { this.v = v }
          |}
          |class Same[T](t: T) extends Box[T](t)
          |class IntBox(i: Int) extends Box[Int](i)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new Same[String]("a").v + new IntBox(7).v
          |  }
          |}
          |""".stripMargin
      val (result, out) = run(src)
      assert(result == Shell.Success("a7"), out)
    }
  }
}
