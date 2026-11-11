package onion.compiler.tools

import onion.tools.Shell

/**
 * A method's defaults are known to every call, whatever the order of the declarations:
 * `def run() = g()` above `def g(n: Int = 5)` compiled to E0005 in one class (#2637), and at
 * the top level, across classes, records, enums, interfaces, extensions and files (#2653).
 */
class DefaultDeclaredLaterSpec extends AbstractShellSpec {
  private def run(src: String): Shell.Result = shell.run(src.stripMargin, "None", Array())

  /** Runs a script and returns what it printed, failing the test if it did not compile. */
  private def output(src: String): String = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val saved = System.out
    val result =
      try {
        System.setOut(ps)
        Console.withOut(ps)(shell.run(src.stripMargin, "DefaultDeclaredLaterScript.on", Array()))
      } finally System.setOut(saved)
    val out = new String(buf.toByteArray, "UTF-8")
    assert(result.isInstanceOf[Shell.Success], s"$result\n$out")
    out.linesIterator.toList.mkString(",")
  }

  private def compiles(files: (String, String)*): Boolean = {
    import onion.compiler.*
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val sources = files.map { case (name, src) =>
      new StreamInputSource(() => new java.io.StringReader(src.stripMargin), name)
    }
    new OnionCompiler(config).compile(sources) match {
      case CompilationOutcome.Success(_) => true
      case CompilationOutcome.Failure(errors) => fail(errors.map(_.message).mkString("\n"))
    }
  }

  describe("a call above the method that declares the default") {
    it("omits a default of a public method declared later") {
      val result = run(
        """
          |class A {
          |public:
          |  def this {}
          |  def run(): Int = g()
          |  def g(n: Int = 5): Int = n
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = "" + new A().run()
          |}
        """)
      assert(Shell.Success("5") == result)
    }

    it("omits a default of a private or static method declared later, and one that reads an earlier parameter") {
      val result = run(
        """
          |class A {
          |public:
          |  def this {}
          |  def run(): String = "" + h() + "," + s() + "," + p(2)
          |private:
          |  def h(a: Int = 1): Int = a
          |  static def s(a: Int = 3): Int = a
          |  def p(a: Int, b: Int = a * 10): Int = a + b
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new A().run()
          |}
        """)
      assert(Shell.Success("1,3,22") == result)
    }
  }

  describe("a call above the declaration, outside one class body (#2653)") {
    it("omits a default of a top-level function declared later") {
      assert(output(
        """
          |def run(): Int = g()
          |def g(n: Int = 7): Int = n
          |println(run())
        """) == "7")
    }

    it("still reads a top-level val or var declared above the function") {
      assert(output(
        """
          |val k = 5
          |var label = "x"
          |def f(x: Int = k, s: String = label): String = s + x
          |println(f())
          |label = "y"
          |println(f())
        """) == "x5,y5")
    }

    it("omits a default of a method, static method or constructor of a class declared later") {
      assert(output(
        """
          |class D {
          |public:
          |  def this {}
          |  def run(): String = "" + new E().g() + "," + E::s() + "," + new B().a
          |}
          |class E {
          |public:
          |  def this {}
          |  def g(n: Int = 9): Int = n
          |  static def s(n: Int = 8): Int = n
          |}
          |class B {
          |public:
          |  val a: Int
          |  def this(a: Int = 7) { this.a = a }
          |}
          |println(new D().run())
        """) == "9,8,7")
    }

    it("passes a named argument into a later class, and delegates by name to a later superclass") {
      assert(output(
        """
          |class D {
          |public:
          |  def this {}
          |  def run(): void { new E().g(n = 2) }
          |}
          |class E {
          |public:
          |  def this {}
          |  def g(m: Int = 1, n: Int = 9): void { println("g" + m + n) }
          |}
          |class C(x: Int) extends A(b = x) { }
          |class A {
          |public:
          |  def this(a: Int = 5, b: Int = 6) { println("A" + a + b) }
          |}
          |new D().run()
          |new C(3)
        """) == "g12,A53")
    }

    it("omits a default declared later in a record, an enum and an interface") {
      assert(output(
        """
          |record R(x: Int) {
          |public:
          |  def run(): Int = g()
          |  def g(n: Int = 4): Int = n + x()
          |}
          |enum Color {
          |  RED
          |public:
          |  def run(): Int = g()
          |  def g(n: Int = 3): Int = n
          |}
          |interface I {
          |  def run(): Int = g()
          |  def g(n: Int = 2): Int = n
          |}
          |class K conforms I {
          |public:
          |  def this {}
          |}
          |println(new R(10).run())
          |println(Color::RED.run())
          |println(new K().run())
        """) == "14,3,2")
    }

    it("omits a default of an extension method declared later") {
      assert(output(
        """
          |def run(): String = "ab".twice()
          |extension String {
          |  def twice(sep: String = "-"): String = self + sep + self
          |}
          |println(run())
        """) == "ab-ab")
    }

    it("compiles a call into another file's default in either file order") {
      val caller = "A.on" -> """
          |class UsesDefault {
          |public:
          |  static def run(): Int = Later::g()
          |}
        """
      val callee = "B.on" -> """
          |class Later {
          |public:
          |  static def g(n: Int = 7): Int = n
          |}
        """
      assert(compiles(caller, callee))
      assert(compiles(callee, caller))
    }
  }

  describe("a default that reads a top-level val") {

    it("still compiles in a class method, a constructor, a record method and an extension") {
      assert(output(
        """
          |val base = 5
          |val greeting = "hi"
          |class K {
          |public:
          |  def f(n: Int = base): Int = n
          |}
          |class C {
          |public:
          |  val x: Int
          |  def this(x: Int = base * 2) { this.x = x }
          |}
          |record R(v: Int) {
          |public:
          |  def f(n: Int = base + v): Int = n
          |}
          |extension String {
          |  def tag(t: String = greeting): String = t + self
          |}
          |println(new K().f())
          |println(new C().x)
          |println(new R(1).f())
          |println("x".tag())
        """) == "5,10,6,hix")
    }

    it("is still seen by a call above it in the same class") {
      assert(output(
        """
          |val base = 3
          |class K {
          |public:
          |  def run(): Int = g()
          |  def g(n: Int = base): Int = n
          |}
          |println(new K().run())
        """) == "3")
    }

    it("still compiles when the val is read only inside a lambda or a trailing lambda") {
      // Panel round 2 on #2798: the scan did not look inside closures, so these were E0002.
      assert(output(
        """
          |val k = 5
          |val xs = [1, 2, 3]
          |val name = "n"
          |def f(fn: () -> Int = () -> k + 1): Int = fn()
          |def j(n: Int = [1, 2].map { x -> x + xs.size }.size): Int = n
          |def s(t: String = "#{name}!"): String = t
          |class C {
          |public:
          |  def h(fn: () -> Int = () -> k + 1): Int = fn()
          |}
          |println(f())
          |println(j())
          |println(s())
          |println(new C().h())
        """) == "6,2,n!,6")
    }

    it("is held back when it calls a method whose default reads a top-level val") {
      // Panel round 3 on #2798: `f`'s default was typed early, before `g`'s held-back default
      // existed, so `g()` was E0005. All three forms print on `main`.
      assert(output(
        """
          |val k = 5
          |def g(b: Int = k): Int = b
          |def f(a: Int = g()): Int = a
          |println(f())
        """) == "5")
      assert(output(
        """
          |val k = 5
          |class A {
          |public:
          |  def g(b: Int = k): Int = b
          |}
          |class B {
          |public:
          |  def f(a: Int = new A().g()): Int = a
          |}
          |println(new B().f())
        """) == "5")
      assert(output(
        """
          |var k = 5
          |def g(b: Int = k): Int = b
          |k = 9
          |def f(a: Int = g() + 1): Int = a
          |println(f())
        """) == "10")
    }

    it("is held back when it calls an inherited held-back default through super") {
      assert(output(
        """
          |val k = 5
          |class Base {
          |public:
          |  def this {}
          |  def g(b: Int = k): Int = b
          |}
          |class Sub extends Base {
          |public:
          |  def this {}
          |  def f(a: Int = super.g()): Int = a
          |}
          |println(new Sub().f())
        """) == "5")
    }

    it("is held back when it constructs a held-back class through a type alias") {
      assert(output(
        """
          |val k = 5
          |class B {
          |public:
          |  val a: Int
          |  def this(a: Int = k) { this.a = a }
          |}
          |type BB = B
          |def f(x: Int = new BB().a): Int = x
          |class C {
          |public:
          |  def this {}
          |  def g(x: Int = new BB().a + 1): Int = x
          |}
          |println(f())
          |println(new C().g())
        """) == "5,6")
    }

    it("is held back when it constructs a held-back class through an import alias") {
      assert(compiles(
        "A.on" ->
          """module p
            |val k = 5
            |class K {
            |public:
            |  val a: Int
            |  def this(a: Int = k) { this.a = a }
            |}
            |""",
        "B.on" ->
          """import { p.K as KK; }
            |class Main {
            |public:
            |  static def f(x: Int = new KK().a): Int = x
            |  static def main(args: String[]): void { println(f()) }
            |}
            |"""))
    }
  }

  describe("a default that calls a same-named method or constructor (panel round 7 on #2798)") {
    it("is held back when the overload it calls is held back") {
      assert(output(
        """
          |val k = 5
          |def g(s: String, b: Int = k): Int = s.length() + b
          |def g(n: Int, d: Int = g("xy")): Int = n + d
          |println(g(100))
        """) == "107")
    }

    it("is held back when the other constructor it calls is held back") {
      assert(output(
        """
          |val k = 5
          |class C {
          |public:
          |  val v: Int
          |  def this(s: String, b: Int = k) { this.v = s.length() + b }
          |  def this(n: Int, d: Int = new C("xy").v) { this.v = n + d }
          |}
          |println(new C(100, 1).v)
        """) == "101")
    }

    it("is held back when another class's same-named method it calls is held back") {
      assert(output(
        """
          |val k = 5
          |class A {
          |public:
          |  def g(b: Int = k): Int = b
          |}
          |class B {
          |public:
          |  def g(n: Int, d: Int = new A().g()): Int = n + d
          |}
          |println(new B().g(100))
        """) == "105")
    }
  }

  describe("a call above a held-back default") {
    def errorsOf(src: String): String = {
      import onion.compiler.*
      val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
      val source = new StreamInputSource(() => new java.io.StringReader(src.stripMargin), "Held.on")
      new OnionCompiler(config).compile(Seq(source)) match {
        case CompilationOutcome.Success(_) => fail("expected a compile error")
        case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.errorCode.getOrElse("")} ${e.message}").mkString("\n")
      }
    }

    it("hints at what holds back a function's default") {
      val text = errorsOf(
        """
          |def run(): Int = f()
          |def f(a: Int = k): Int = a
          |val k = 5
          |println(run())
        """)
      assert(text.contains("E0005"), text)
      assert(text.contains("`k`"), text)
    }

    it("hints at what holds back a constructor's default") {
      val text = errorsOf(
        """
          |def make(): Int = new K().a
          |class K {
          |public:
          |  val a: Int
          |  def this(a: Int = k) { this.a = a }
          |}
          |val k = 5
          |println(make())
        """)
      assert(text.contains("E0021"), text)
      assert(text.contains("`k`"), text)
    }

    it("gives no such hint when an argument has the wrong type") {
      val text = errorsOf(
        """
          |def run(): Int = g("s")
          |val base = 7
          |def g(a: Int, n: Int = base): Int = a + n
        """)
      assert(text.contains("E0005"), text)
      assert(!text.contains("`base`"), text)
    }

    it("gives no such hint for a constructor when an argument has the wrong type") {
      val text = errorsOf(
        """
          |def make(): Int = new K("s").a
          |class K {
          |public:
          |  val a: Int
          |  def this(x: Int, y: Int = k) { this.a = x + y }
          |}
          |val k = 5
        """)
      assert(text.contains("E0021"), text)
      assert(!text.contains("`k`"), text)
    }

    it("gives no such hint when the argument is passed") {
      val text = errorsOf(
        """
          |def run(): Int = f("x")
          |def f(a: Int = k): Int = a
          |val k = 5
          |println(run())
        """)
      assert(!text.contains("`k`"), text)
    }
  }

  describe("a default that reads a plain constructor parameter") {
    it("still hints that the parameter is not a field") {
      import onion.compiler.*
      val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
      val src =
        """class P(v: Int) {
          |public:
          |  def get(d: Int = v): Int = d
          |}
          |println(new P(1).get())
          |""".stripMargin
      val source = new StreamInputSource(() => new java.io.StringReader(src), "P.on")
      new OnionCompiler(config).compile(Seq(source)) match {
        case CompilationOutcome.Success(_) => fail("expected E0002")
        case CompilationOutcome.Failure(errors) =>
          val text = errors.map(_.message).mkString("\n")
          assert(text.contains("`val v`"), text)
      }
    }
  }

  describe("a default that omits another method's default") {
    it("compiles whichever of the two is declared first") {
      assert(output(
        """
          |def f(a: Int = g()): Int = a
          |def g(b: Int = 2): Int = b
          |class C {
          |public:
          |  def p(a: Int = new D().q() + 1): Int = a
          |}
          |class D {
          |public:
          |  def q(b: Int = 4): Int = b
          |}
          |println(f())
          |println(new C().p())
        """) == "2,5")
    }
  }
}
