package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

/**
 * A default argument is evaluated in the callee, as if at the start of its body: it sees the
 * callee's `this` (the call's receiver), the class's private members and the parameters
 * declared before it (#2375, #2358). It used to be spliced into each call site, where none of
 * those exist, so such defaults failed JVM verification, threw IllegalAccessError, or read the
 * caller's state instead of the receiver's.
 */
class DefaultArgumentScopeSpec extends AbstractShellSpec {
  private def run(src: String): Shell.Result = shell.run(src.stripMargin, "None", Array())

  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src.stripMargin), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
      case _ => Seq.empty
    }
  }

  describe("a default that reads an instance member (#2375)") {
    it("reads the receiver's field, from inside and outside the class") {
      val result = run(
        """
          |class C {
          |  val k: Int
          |public:
          |  def this(k: Int) { this.k = k }
          |  def f(n: Int, m: Int = k): Int = n + m
          |  def g(): Int = f(1)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new C(7).f(1) + "," + new C(7).g()
          |}
        """)
      assert(Shell.Success("8,8") == result)
    }

    it("uses the call's receiver, not the caller's this") {
      val result = run(
        """
          |class N {
          |  val v: Int
          |public:
          |  def this(v: Int) { this.v = v }
          |  def get(): Int = v
          |  def f(n: Int, o: N = this): Int = n + o.get()
          |  def other(x: N): Int = x.f(0)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new N(1)
          |    val b = new N(2)
          |    return a.f(0) + "," + b.f(0) + "," + a.other(b) + "," + a.f(0, b)
          |  }
          |}
        """)
      assert(Shell.Success("1,2,2,2") == result)
    }

    it("may read a private static field from another class's call site") {
      val result = run(
        """
          |class C {
          |  static val s: Int = 5
          |public:
          |  def this {}
          |  def f(n: Int, m: Int = s): Int = n + m
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = "" + new C().f(1)
          |}
        """)
      assert(Shell.Success("6") == result)
    }

    it("works through a safe call and inside a lambda") {
      val result = run(
        """
          |class K {
          |  val base: Int
          |public:
          |  def this(b: Int) { base = b }
          |  def f(n: Int, m: Int = base + n): Int = n + m
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val k: K? = new K(10)
          |    val none: K? = null
          |    val xs = [1, 2].map { x -> new K(x).f(x) }
          |    return k?.f(1) + "," + none?.f(1) + "," + xs
          |  }
          |}
        """)
      assert(Shell.Success("12,null,[3, 6]") == result)
    }

    it("super.f() runs the parent's default, an override's own default runs otherwise") {
      val result = run(
        """
          |class A {
          |  val k: Int = 5
          |public:
          |  def this {}
          |  def f(n: Int = k): Int = n
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  override def f(n: Int = 9): Int = n * 100
          |  def g(): Int = super.f()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new B().g() + "," + new B().f()
          |}
        """)
      assert(Shell.Success("5,900") == result)
    }
  }

  describe("a default that reads an earlier parameter (#2358)") {
    it("sees the argument passed for it") {
      val result = run(
        """
          |class Test {
          |public:
          |  static def g(n: Int, m: Int = n + 1): Int = n + m
          |  static def h(a: Int, b: Int = a + 1, c: Int = b * 2): String = a + "," + b + "," + c
          |  static def main(args: String[]): String = g(1) + " " + g(1, 5) + " " + h(1) + " " + h(1, c = 5) + " " + h(a = 2)
          |}
        """)
      assert(Shell.Success("3 6 1,2,4 1,2,5 2,3,6") == result)
    }

    it("prefers the parameter over a same-named top-level val, field or caller local") {
      val result = run(
        """
          |val n = 100
          |def g(n: Int, m: Int = n + 1): Int = n + m
          |def caller(): Int {
          |  val n = 7
          |  return g(1) + n
          |}
          |class C(val n: Int) {
          |public:
          |  def g(n: Int, m: Int = n + 1): Int = n + m
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = g(1) + "," + caller() + "," + new C(100).g(1)
          |}
        """)
      assert(Shell.Success("3,10,3") == result)
    }

    it("works for a primary-constructor parameter and a generic method") {
      val result = run(
        """
          |class Q(val a: Int, val b: Int = a * 2)
          |class Box[T](val v: T, val w: T = v)
          |class Test {
          |public:
          |  static def pick[T](a: T, b: T = a): T = b
          |  static def main(args: String[]): String = new Q(3).b + "," + new Box[String]("q").w + "," + pick("s") + pick(4)
          |}
        """)
      assert(Shell.Success("6,q,s4") == result)
    }

    it("keeps a tail call that omits a defaulted argument a loop") {
      val result = run(
        """
          |class Test {
          |public:
          |  static def loop(n: Int, acc: Int = n * 2): Int = if n == 0 { acc } else { loop(n - 1) }
          |  static def main(args: String[]): String = "" + loop(100000)
          |}
        """)
      assert(Shell.Success("0") == result)
    }

    it("counts a parameter read only by a later default as used (no W0006)") {
      val out = new java.io.ByteArrayOutputStream()
      val saved = System.err
      System.setErr(new java.io.PrintStream(out, true, "UTF-8"))
      val result =
        try run(
          """
            |class Test {
            |public:
            |  static def clo(n: Int, g: () -> Int = () -> n * 2): Int = g()
            |  static def main(args: String[]): String = "" + clo(21)
            |}
          """)
        finally System.setErr(saved)
      assert(Shell.Success("42") == result)
      assert(!out.toString("UTF-8").contains("W0006"))
    }
  }

  describe("a default's type (#2541)") {
    it("is checked against the parameter, as `val b: Byte = 200` is") {
      val byte = errors(
        """
          |def f(p: Int, b: Byte = 200): Int = p + b
          |println(f(1))
        """)
      assert(byte.flatMap(_.errorCode) == Seq("E0000"))
      val short = errors(
        """
          |def f(p: Int, s: Short = 3.7): Int = p + s
          |println(f(1))
        """)
      assert(short.flatMap(_.errorCode) == Seq("E0000"))
    }

    it("still narrows an Int constant that fits, and widens as before") {
      val result = run(
        """
          |class Test {
          |public:
          |  static def a(b: Byte = 3, s: Short = -4, l: Long = 1, d: Double = 1, o: Object = 1, x: Int? = null): String =
          |    b + " " + s + " " + l + " " + d + " " + o + " " + x
          |  static def main(args: String[]): String = a()
          |}
        """)
      assert(Shell.Success("3 -4 1 1.0 1 null") == result)
    }
  }

  describe("a default that names its own or a later parameter") {
    it("is E0002 at the name, even when a top-level val shares it, and no E0005 at the call") {
      val errs = errors(
        """
          |val later = 100
          |def g(m: Int = later, later: Int = 1): Int = m + later
          |println(g())
        """)
      assert(errs.flatMap(_.errorCode) == Seq("E0002"))
      assert(errs.head.location.line == 3)
      assert(errs.head.message.contains("later"))
    }
  }
}
