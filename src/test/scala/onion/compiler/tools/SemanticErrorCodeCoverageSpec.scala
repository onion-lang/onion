package onion.compiler.tools

import onion.tools.Shell

/**
 * One trigger per reachable-but-previously-unasserted diagnostic code (0.10.1 quality
 * sweep, continuing the per-code coverage #407-#412 started). Each case compiles a
 * minimal program and asserts the *code* appears in the diagnostics — codes are
 * locale-independent where message text is not.
 *
 * Deliberately absent:
 *   - E0075 LAW_CLASS_NOT_LOADABLE — reachable only through an environment failure
 *     (a compiled check class failing to load), which has no honest in-process trigger.
 *   - E0032 TYPE_ARGUMENT_MUST_BE_REFERENCE — has live report sites (checked whenever
 *     a *written* type argument maps to `void`), but is not registered dead here: the
 *     drift guard below only asks whether a report call exists, and it does. What has
 *     no honest in-process trigger is the *input*: `type()` in the grammar (used for
 *     every type-argument and type-alias-target position) never routes through
 *     `void_type()` — only `return_type()` does — so `void`/`Unit` cannot be written
 *     as a type argument at all (`new Box[void]()`, `Box[void]`, `type T = void` are
 *     all syntax errors, not E0032). The check appears to guard a substitution the
 *     current grammar cannot produce.
 */
class SemanticErrorCodeCoverageSpec extends AbstractShellSpec {

  /** Codes declared but never reported. Empty since #427 deleted the three that were:
   *  CYCLIC_DELEGATION (undecidable — the delegate is chosen at runtime),
   *  UNIMPLEMENTED_FEATURE (leftover) and ENUM_CONSTANT_ARGS_UNSUPPORTED (obsolete
   *  once data-carrying enums shipped). Registering a code here is how a genuinely
   *  unreachable one gets acknowledged instead of rotting unnoticed. */
  private val deadCodes = Set.empty[String]

  private def failsWith(code: String, source: String): Unit = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (o, e) = (System.out, System.err)
    val r =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array()) } }
      } finally { System.setOut(o); System.setErr(e) }
    val out = new String(buf.toByteArray, "UTF-8")
    assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure for $code, got $r\n$out")
    assert(out.contains(code), s"expected $code in diagnostics, got:\n$out")
  }

  describe("declaration-level duplicates and cycles") {
    it("E0008 duplicate class") {
      failsWith("E0008",
        """class A { public: def this {} }
          |class A { public: def this {} }
          |""".stripMargin)
    }
    it("E0009 duplicate field") {
      failsWith("E0009",
        """class A {
          |  var x: Int
          |  var x: Int
          |public:
          |  def this {}
          |}
          |""".stripMargin)
    }
    it("E0025 duplicate constructor") {
      failsWith("E0025",
        """class A {
          |public:
          |  def this(x: Int) {}
          |  def this(y: Int) {}
          |}
          |""".stripMargin)
    }
    it("E0029 duplicate type parameter") {
      failsWith("E0029",
        """class Box[T, T] { public: def this {} }
          |""".stripMargin)
    }
    it("E0053 cyclic type alias — and it renders its message, not `Unknown error`") {
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val (o, e) = (System.out, System.err)
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) {
          shell.run("type A = B\ntype B = A\n", "None", Array())
        }}
      } finally { System.setOut(o); System.setErr(e) }
      val out = new String(buf.toByteArray, "UTF-8")
      assert(out.contains("E0053"), out)
      assert(!out.contains("Unknown error"), s"E0053 must render its message:\n$out")
    }
    it("E0054 duplicate type alias — same rendering guarantee") {
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val (o, e) = (System.out, System.err)
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) {
          shell.run("type A = java.lang.String\ntype A = java.lang.Integer\n", "None", Array())
        }}
      } finally { System.setOut(o); System.setErr(e) }
      val out = new String(buf.toByteArray, "UTF-8")
      assert(out.contains("E0054"), out)
      assert(!out.contains("Unknown error"), s"E0054 must render its message:\n$out")
    }
  }

  describe("basic type and name resolution") {
    it("E0000 incompatible type in a val's declared type") {
      failsWith("E0000",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = "a"
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0001 incompatible operand type for +") {
      failsWith("E0001",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1 + true
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0002 variable not found") {
      failsWith("E0002",
        """class Test {
          |public:
          |  static def main(args: String[]): Int { return undefinedVar }
          |}
          |""".stripMargin)
    }
    it("E0003 class not found") {
      failsWith("E0003",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: NoSuchClass = null
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0004 field not found") {
      failsWith("E0004",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s = "a".noSuchField
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0005 method not found") {
      failsWith("E0005",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s = "a".noSuchMethod()
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0006 ambiguous method (null argument fits two unrelated overloads equally)") {
      failsWith("E0006",
        """class A {}
          |class B {}
          |class Test {
          |public:
          |  static def foo(x: A): Int = 1
          |  static def foo(x: B): Int = 2
          |  static def main(args: String[]): Int {
          |    foo(null)
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0007 duplicate local variable") {
      failsWith("E0007",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 1
          |    val x = 2
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
  }

  describe("more declaration-level duplicates") {
    it("E0010 duplicate method") {
      failsWith("E0010",
        """class Test {
          |public:
          |  def m(): Int = 1
          |  def m(): Int = 2
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
    }
    it("E0012 duplicate top-level function") {
      failsWith("E0012",
        """def f(): Int = 1
          |def f(): Int = 2
          |def main(): void { }
          |""".stripMargin)
    }
  }

  describe("member accessibility") {
    it("E0013 private static method called from another class") {
      failsWith("E0013",
        """class C {
          |private:
          |  static def s(): Int = 1
          |}
          |def main(args: String[]): void { IO::println(C::s()) }
          |""".stripMargin)
    }
    it("E0014 writing a non-public field from outside its class") {
      failsWith("E0014",
        """class Plain {
          |  var value: String
          |public:
          |  def this(v: String) { value = v }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val p = new Plain("orig")
          |    p.value = "changed"
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
  }

  describe("inheritance and interfaces") {
    it("E0018 illegal inheritance (extending a final class)") {
      failsWith("E0018",
        """class A extends java.lang.String { public: def this {} }
          |""".stripMargin)
    }
    it("E0023 a forward-delegation field requires an interface type") {
      failsWith("E0023",
        """class A {
          |  forward val x: String
          |public:
          |  def this { x = "a" }
          |}
          |""".stripMargin)
    }
    it("E0037 unimplemented abstract method") {
      failsWith("E0037",
        """interface I { def m(): Int }
          |class A conforms I { public: def this {} }
          |""".stripMargin)
    }
    it("E0039 overriding a final method") {
      failsWith("E0039",
        """class A {
          |public:
          |  def this {}
          |  final def m(): Int = 1
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  override def m(): Int = 2
          |}
          |""".stripMargin)
    }
  }

  describe("calls, constructors and arguments") {
    it("E0021 constructor not found") {
      failsWith("E0021",
        """class A { public: def this {} }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { val a = new A(1, 2); return 0 }
          |}
          |""".stripMargin)
    }
    it("E0022 ambiguous constructor") {
      failsWith("E0022",
        """class C {
          |public:
          |  def this(a: String) {}
          |  def this(b: StringBuilder) {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { val c = new C(null); return 0 }
          |}
          |""".stripMargin)
    }
    it("E0019 calling a static method through an instance receiver") {
      failsWith("E0019",
        """class A {
          |public:
          |  def this {}
          |  static def s(): Int = 1
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return new A().s() }
          |}
          |""".stripMargin)
    }
    it("E0044 duplicate named argument") {
      failsWith("E0044",
        """class Test {
          |public:
          |  static def f(x: Int, y: Int): Int = x + y
          |  static def main(args: String[]): Int { return f(x = 1, x = 2) }
          |}
          |""".stripMargin)
    }
    it("E0045 positional after named") {
      failsWith("E0045",
        """class Test {
          |public:
          |  static def f(x: Int, y: Int): Int = x + y
          |  static def main(args: String[]): Int { return f(x = 1, 2) }
          |}
          |""".stripMargin)
    }
    it("E0043 unknown named argument on a static call (#426)") {
      failsWith("E0043",
        """class T {
          |public:
          |  static def f(x: Int, y: Int): Int = x + y
          |  static def main(args: String[]): Int { return f(x = 1, nope = 2) }
          |}
          |""".stripMargin)
    }
    it("E0043 unknown named argument on an instance call") {
      failsWith("E0043",
        """class T {
          |public:
          |  def this {}
          |  def f(x: Int, y: Int): Int = x + y
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return new T().f(x = 1, nope = 2) }
          |}
          |""".stripMargin)
    }
    it("E0043 unknown named argument on an unqualified call") {
      failsWith("E0043",
        """class T {
          |public:
          |  def this {}
          |  def f(x: Int, y: Int): Int = x + y
          |  def g(): Int = f(x = 1, nope = 2)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return new T().g() }
          |}
          |""".stripMargin)
    }
    it("E0043 unknown named argument on a constructor call") {
      failsWith("E0043",
        """class P {
          |  val x: Int
          |  val y: Int
          |public:
          |  def this(x: Int, y: Int) { self.x = x; self.y = y }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { val p = new P(x = 1, nope = 2); return 0 }
          |}
          |""".stripMargin)
    }
  }

  describe("generics") {
    it("E0030 type is not generic") {
      failsWith("E0030",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: String[Int] = null
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0031 type argument arity mismatch") {
      failsWith("E0031",
        """class Box[T] { public: def this {} }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b = new Box[String, String]()
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0031 also fires in a nullable type annotation (issue #413)") {
      failsWith("E0031",
        """class Box[T] { public: def this {} }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Box[String, String]? = null
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0033 method is not generic") {
      failsWith("E0033",
        """class Test {
          |public:
          |  static def main(args: String[]): Int { return "abc".length[String]() }
          |}
          |""".stripMargin)
    }
    it("E0034 method type argument arity mismatch") {
      failsWith("E0034",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val l = java.util.Collections::emptyList[String, String]()
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0035 erasure signature collision") {
      failsWith("E0035",
        """class C {
          |public:
          |  def this {}
          |  def f(x: java.util.List[String]): Int = 1
          |  def f(x: java.util.List[Integer]): Int = 2
          |}
          |""".stripMargin)
    }
  }

  describe("statements and expressions") {
    it("E0020 a bare return in a value-returning method") {
      failsWith("E0020",
        """class Test {
          |public:
          |  static def f(): Int { return }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
    }
    it("E0027 void is not boxable") {
      failsWith("E0027",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s = "a" + IO::println("b")
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0028 assignment needs an lvalue") {
      failsWith("E0028",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    "a".length() = 3
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0040 calling a method on a void result") {
      failsWith("E0040",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    IO::println("a").toString()
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0050 `this` in a static context") {
      failsWith("E0050",
        """class Test {
          |public:
          |  static def s(): Int { return this.hashCode() }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
    }
    it("E0073 a Map is not directly iterable") {
      failsWith("E0073",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    foreach x: Object in ["a": 1] { }
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
  }

  describe("declaration shapes") {
    it("E0051 recursive function needs a return type") {
      failsWith("E0051",
        """def f(n: Int) = f(n)
          |def main(): void { }
          |""".stripMargin)
    }
    it("E0055 a function needs a body") {
      failsWith("E0055",
        """def f(): Int;
          |def main(): void { }
          |""".stripMargin)
    }
    it("E0081 a tool parameter that cannot be read from the command line (#424)") {
      failsWith("E0081",
        """record P(x: Int)
          |tool t(p: P): Int requires { console } { IO::println("" + p.x()); return 0 }
          |""".stripMargin)
    }
    it("E0082 two tools with the same name (#436)") {
      failsWith("E0082",
        """tool same(a: Int): Int requires { console } { IO::println("A"); return 0 }
          |tool same(b: String): Int requires { console } { IO::println("B"); return 0 }
          |""".stripMargin)
    }
    it("E0076 unknown shape format") {
      failsWith("E0076",
        """record P(x: Int)
          |  shape d = toml
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
    }
  }

  describe("closures and destructuring") {
    it("E0052 lambda parameter needs a type annotation when none can be inferred") {
      failsWith("E0052",
        """val f = (x) -> x + 1
          |def main(): void { }
          |""".stripMargin)
    }
    it("E0046 destructuring binding count must match the record's field count") {
      failsWith("E0046",
        """record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val (a, b, c) = new Point(1, 2)
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
    it("E0047 destructuring a non-record value") {
      failsWith("E0047",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val (a, b) = "not a record"
          |    return 0
          |  }
          |}
          |""".stripMargin)
    }
  }

  describe("constructors") {
    it("E0087 a def this in a class with a primary constructor that does not delegate") {
      // `new P()` used to compile and leave x at 0: the primary's field stores were in the
      // synthesized constructor's own block, which a sibling never ran.
      failsWith("E0087",
        """
          |class P(val x: Int) {
          |public:
          |  def this { }
          |}
          |class Test { public: static def main(args: String[]): String { return "" + new P().x } }
          |""".stripMargin)
    }

    it("E0087 also when the primary is only an extends(args) clause") {
      // No parameter list, but arguments on `extends`: that is a no-arg primary, and the
      // `def this(x)` below used to resolve super() against Base's *no-arg* constructor.
      failsWith("E0087",
        """
          |class Base { public: def this(v: Int) { } }
          |class Sub extends Base(1) {
          |public:
          |  def this(x: Int) { }
          |}
          |""".stripMargin)
    }

    it("E0088 constructor delegation cycle") {
      failsWith("E0088",
        """
          |class C {
          |public:
          |  def this(a: Int) : this("s") { }
          |  def this(s: String) : this(1) { }
          |}
          |""".stripMargin)
    }

    it("E0088 a constructor delegating to itself") {
      failsWith("E0088",
        """
          |class C {
          |public:
          |  def this(a: Int) : this(a) { }
          |}
          |""".stripMargin)
    }

    it("E0089 def this in a record body") {
      // Was a compiler crash (I0000): never typed, then codegen emitted the record's
      // synthetic field stores against a constructor of the wrong arity.
      failsWith("E0089",
        """
          |record R(a: Int, b: Int) {
          |public:
          |  def this(a: Int) { }
          |}
          |""".stripMargin)
    }

    it("E0089 def this in an enum body") {
      failsWith("E0089",
        """
          |enum E {
          |  A, B
          |public:
          |  def this { }
          |}
          |""".stripMargin)
    }

    it("E0090 a field read inside delegation arguments") {
      // GETFIELD on uninitializedThis: the verifier rejected the class at load time and
      // the compiler reported I0000.
      failsWith("E0090",
        """
          |class F(val x: Int) {
          |public:
          |  var seed: Int = 3
          |  def this : this(seed) { }
          |}
          |""".stripMargin)
    }

    it("E0090 an explicit this inside super arguments") {
      failsWith("E0090",
        """
          |class B { public: def this(x: Int) { } }
          |class D extends B(this.hashCode()) { }
          |""".stripMargin)
    }
  }

  describe("drift guard: the dead-code registry") {
    it("codes listed as dead have no report site; every other code has one") {
      val srcDir = java.nio.file.Path.of("src/main/scala")
      val sources = java.nio.file.Files.walk(srcDir).iterator()
      val sb = new StringBuilder
      sources.forEachRemaining { p =>
        // The reporter is rendering, not reporting — it names every code by design.
        if (p.toString.endsWith(".scala") && !p.toString.endsWith("SemanticError.scala")
            && !p.toString.endsWith("SemanticErrorReporter.scala"))
          sb.append(java.nio.file.Files.readString(p)).append('\n')
      }
      val allMain = sb.toString
      val declared = """case object (\w+) extends SemanticError""".r
        .findAllMatchIn(java.nio.file.Files.readString(
          java.nio.file.Path.of("src/main/scala/onion/compiler/SemanticError.scala")))
        .map(_.group(1)).toSet
      val undeadDead = deadCodes.filter(allMain.contains)
      assert(undeadDead.isEmpty,
        s"codes listed as dead now have report sites — cover them and update the registry: $undeadDead")
      val newlyDead = (declared -- deadCodes).filterNot(allMain.contains)
      assert(newlyDead.isEmpty,
        s"codes with no report site anywhere — dead diagnostics deserve flagging: $newlyDead")
    }
  }
}
