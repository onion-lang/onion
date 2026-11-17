package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0068 (OVERRIDE_TARGET_NOT_FOUND) is reported when a method marked `override`
 * does not override any base-class or interface method, but had no regression
 * coverage at all before this spec.
 */
class OverrideTargetNotFoundSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("a method marked override with no base target") {
    it("reports E0068 when the base class declares no method of that name") {
      val results = errors(
        """
          |class Base {
          |public:
          |  def helper(): Int { return 1; }
          |}
          |class Sub extends Base {
          |public:
          |  override def notInBase(): Int { return 2; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Sub().notInBase();
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0068")))
    }

    it("reports E0068 when there is no explicit supertype at all") {
      val results = errors(
        """
          |class Standalone {
          |public:
          |  override def phantom(): Int { return 3; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Standalone().phantom();
          |  }
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0068")))
    }

    it("does not report E0068 when correctly overriding a base class method") {
      val results = errors(
        """
          |class Base {
          |public:
          |  def greet(): String { return "base"; }
          |}
          |class Sub extends Base {
          |public:
          |  override def greet(): String { return "sub"; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Sub().greet().length();
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0068")))
    }

    it("does not report E0068 when correctly implementing an interface method") {
      val results = errors(
        """
          |interface Greeter {
          |  def greet(): String;
          |}
          |class G conforms Greeter {
          |public:
          |  override def greet(): String { return "hi"; }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return (new G() as Greeter).greet().length();
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0068")))
    }

    it("suggests the closest base-class method name for a near-miss typo") {
      // Assert on the error code and the suggested identifier only (both
      // locale-independent) rather than the localized "did you mean" wording.
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val (o, e) = (System.out, System.err)
      val r =
        try {
          System.setOut(ps); System.setErr(ps)
          Console.withOut(ps) { Console.withErr(ps) {
            shell.run(
              """
                |class Base {
                |public:
                |  def greet(): String { return "base"; }
                |}
                |class Sub extends Base {
                |public:
                |  override def great(): String { return "sub"; }
                |}
                |class Test {
                |public:
                |  static def main(args: String[]): Int {
                |    return new Sub().great().length();
                |  }
                |}
                |""".stripMargin,
              "None",
              Array()
            )
          } }
        } finally { System.setOut(o); System.setErr(e) }
      val out = new String(buf.toByteArray, "UTF-8")
      assert(r.isInstanceOf[onion.tools.Shell.Failure], s"expected a compile failure, got $r\n$out")
      assert(out.contains("E0068"), s"expected E0068 in diagnostics, got:\n$out")
      assert(out.contains("greet"), s"expected suggestion 'greet' in diagnostics, got:\n$out")
    }
  }

  describe("an override that matches a generic contract only through boxing (#2681)") {
    it("reports E0068 when a sibling overload is the exact implementation") {
      val results = errors(
        """
          |interface G[T] { def n(x: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  override def n(x: Int): JInteger = 1
          |  def n(x: JInteger): JInteger = 2
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0068")))
    }

    it("still accepts the boxed override next to an unmarked primitive overload") {
      val results = errors(
        """
          |interface G[T] { def n(x: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int): JInteger = 1
          |  override def n(x: JInteger): JInteger = 2
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0068")))
    }

    it("accepts a boxed override when the sibling matches a different base overload (#2921)") {
      val results = errors(
        """
          |interface G[T] { def n(x: T): String; def n(x: String): String }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  override def n(x: Int): String = "int"
          |  override def n(x: String): String = "str"
          |}
          |""".stripMargin
      )
      assert(results.isEmpty, s"expected no errors, got: $results")
    }

    it("accepts a boxed override when the other overload comes from a second interface (#2921)") {
      val results = errors(
        """
          |interface A[T] { def n(x: T): String }
          |interface B { def n(x: String): String }
          |class C conforms A[JInteger], B {
          |public:
          |  def this {}
          |  override def n(x: Int): String = "int"
          |  override def n(x: String): String = "str"
          |}
          |""".stripMargin
      )
      assert(results.isEmpty, s"expected no errors, got: $results")
    }

    it("accepts a boxed override of a generic abstract class next to another overload (#2921)") {
      val results = errors(
        """
          |abstract class Base[T] {
          |public:
          |  def this {}
          |  abstract def n(x: T): String
          |  abstract def n(x: String): String
          |}
          |class C extends Base[JLong] {
          |public:
          |  def this {}
          |  override def n(x: Long): String = "long"
          |  override def n(x: String): String = "str"
          |}
          |""".stripMargin
      )
      assert(results.isEmpty, s"expected no errors, got: $results")
    }

    it("still reports E0068 when the sibling exactly matches the same base overload (#2921)") {
      val results = errors(
        """
          |interface G[T] { def n(x: T): String; def n(x: String): String }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  override def n(x: Int): String = "int"
          |  def n(x: JInteger): String = "boxed"
          |  override def n(x: String): String = "str"
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
    }
  }
  describe("an override of a non-generic base method that differs by `?` or by boxing (#2896)") {
    // Through a non-generic supertype, `k: Int?` for an inherited `k: Int` (or the reverse)
    // is bridged, so it overrides whatever the return type. `k: JInteger` for `k: Int` is
    // not bridged: it is an overload, so `override` on it is E0068, as in Java.
    def program(baseParam: String, subParam: String, ret: String) = {
      def body(who: String) = ret match
        case "void" => s"""who = "$who""""
        case "Int" => s"""who = "$who"; return 1"""
        case _ => s"""who = "$who"; return "$who""""
      s"""
         |class Base {
         |public:
         |  var who: String
         |  def this { who = "" }
         |  def f(k: $baseParam): $ret { ${body("Base")} }
         |}
         |class Up extends Base {
         |public:
         |  def this {}
         |  override def f(k: $subParam): $ret { ${body("Up")} }
         |}
         |""".stripMargin
    }

    // Calls `f` through the base type and returns who ran.
    def whoRuns(baseParam: String, subParam: String, ret: String): onion.tools.Shell.Result =
      shell.run(
        program(baseParam, subParam, ret) +
          """class Test {
            |public:
            |  static def main(args: String[]): String { val b: Base = new Up(); b.f(3); return b.who }
            |}
            |""".stripMargin,
        "BridgedOverride.on", Array())

    it("runs `override def f(k: Int?): void` through the base type") {
      assert(whoRuns("Int", "Int?", "void") == onion.tools.Shell.Success("Up"))
    }

    it("runs `override def f(k: Int): void` over `f(k: Int?)`") {
      assert(whoRuns("Int?", "Int", "void") == onion.tools.Shell.Success("Up"))
    }

    it("runs `override def f(k: Int?): String` over `f(k: Int): String`") {
      assert(whoRuns("Int", "Int?", "String") == onion.tools.Shell.Success("Up"))
    }

    it("runs `override def f(k: Int?): Int` over `f(k: Int): Int`") {
      assert(whoRuns("Int", "Int?", "Int") == onion.tools.Shell.Success("Up"))
    }

    // The base declares both `f(k: Int)` and `f(k: Int?)`; `Up` overrides only the second.
    // No `?` bridge may take over the first (panel block on #3051 at eef87b7).
    def bothOverloads(ret: String): onion.tools.Shell.Result = {
      def body(who: String) = if ret == "void" then s"""who = who + "$who;"""" else s"""who = who + "$who;"; return "$who""""
      shell.run(
        s"""
           |class Base {
           |public:
           |  var who: String
           |  def this { who = "" }
           |  def f(k: Int): $ret { ${body("Base.f(Int)")} }
           |  def f(k: Int?): $ret { ${body("Base.f(Int?)")} }
           |}
           |class Up extends Base {
           |public:
           |  def this {}
           |  override def f(k: Int?): $ret { ${body("Up.f(Int?)")} }
           |}
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    val b: Base = new Up(); b.f(3); val j: Int? = 4; b.f(j); return b.who
           |  }
           |}
           |""".stripMargin,
        "BothOverloads.on", Array())
    }

    it("leaves an inherited `f(k: Int)` alone when only `f(k: Int?)` is overridden (void)") {
      assert(bothOverloads("void") == onion.tools.Shell.Success("Base.f(Int);Up.f(Int?);"))
    }

    it("leaves an inherited `f(k: Int)` alone when only `f(k: Int?)` is overridden (String)") {
      assert(bothOverloads("String") == onion.tools.Shell.Success("Base.f(Int);Up.f(Int?);"))
    }

    // An exact inherited slot that is only abstract (`I.f(Int)` beside `J.f(Int?)`) still
    // needs the `?` bridge: nothing else fills it (panel block on #3051 at 2bcde1e).
    def abstractExactSlot(second: String, ret: String): onion.tools.Shell.Result = {
      val (secondDecl, conformsClause) = second match
        case "interface" => (s"interface J { def f(k: Int?): $ret }", "conforms I, J")
        case _ => (s"abstract class J { public: def this {} abstract def f(k: Int?): $ret }", "extends J conforms I")
      val body = if ret == "void" then "who = \"C\"" else "who = \"C\"; return \"C\""
      shell.run(
        s"""
           |interface I { def f(k: Int): $ret }
           |$secondDecl
           |class C $conformsClause {
           |public:
           |  var who: String
           |  def this { who = "" }
           |  override def f(k: Int?): $ret { $body }
           |}
           |class Test {
           |public:
           |  static def main(args: String[]): String { val c = new C(); val i: I = c; i.f(1); return c.who }
           |}
           |""".stripMargin,
        "AbstractExactSlot.on", Array())
    }

    it("bridges `f(k: Int?)` to an abstract interface `f(k: Int)` beside `J.f(k: Int?)` (String)") {
      assert(abstractExactSlot("interface", "String") == onion.tools.Shell.Success("C"))
    }

    it("bridges `f(k: Int?)` to an abstract interface `f(k: Int)` beside `J.f(k: Int?)` (void)") {
      assert(abstractExactSlot("interface", "void") == onion.tools.Shell.Success("C"))
    }

    it("bridges `f(k: Int?)` to an abstract interface `f(k: Int)` beside an abstract class's `f(k: Int?)`") {
      assert(abstractExactSlot("abstract", "String") == onion.tools.Shell.Success("C"))
    }

    // The class's own slot `f(Int?)` is inherited with a body, but the slot the bridge
    // fills, `I.f(Int)`, is still abstract: the bridge is the only code for it
    // (panel block on #3051 at 17ebd20).
    def concreteOwnAbstractTarget(provider: String): onion.tools.Shell.Result = {
      val (decls, header) = provider match
        case "class" =>
          ("""interface I { def f(k: Int): String }
             |class Base {
             |public:
             |  def this {}
             |  def f(k: Int?): String = "Base"
             |}""".stripMargin, "extends Base conforms I")
        case "default" =>
          ("""interface I { def f(k: Int): String }
             |interface J { def f(k: Int?): String = "J" }""".stripMargin, "conforms I, J")
        case _ =>
          ("""interface J { def f(k: Int?): String = "J" }
             |abstract class A {
             |public:
             |  def this {}
             |  abstract def f(k: Int): String
             |}""".stripMargin, "extends A conforms J")
      val call = if provider == "abstract" then "val a: A = new D(); return a.f(1)" else "val i: I = new D(); return i.f(1)"
      shell.run(
        s"""
           |$decls
           |class D $header {
           |public:
           |  def this {}
           |  override def f(k: Int?): String = "D"
           |}
           |class Test {
           |public:
           |  static def main(args: String[]): String { $call }
           |}
           |""".stripMargin,
        "ConcreteOwnAbstractTarget.on", Array())
    }

    it("bridges to an abstract `I.f(k: Int)` when `f(k: Int?)` is inherited from a class") {
      assert(concreteOwnAbstractTarget("class") == onion.tools.Shell.Success("D"))
    }

    it("bridges to an abstract `I.f(k: Int)` when `f(k: Int?)` is an interface default") {
      assert(concreteOwnAbstractTarget("default") == onion.tools.Shell.Success("D"))
    }

    it("bridges to an abstract class's `f(k: Int)` when `f(k: Int?)` is an interface default") {
      assert(concreteOwnAbstractTarget("abstract") == onion.tools.Shell.Success("D"))
    }

    // A chain of `?` overrides: `B`'s bridge and its `f(Int?)` are one method, so `C`
    // overriding either one overrides both (panel block 5 on #3051 at 1218324).
    def chain(aParam: String, bParam: String, cParam: String): onion.tools.Shell.Result =
      shell.run(
        s"""
           |class A { public: def this {} def f(x: $aParam): String = "A" }
           |class B extends A { public: def this {} override def f(x: $bParam): String = "B" }
           |class C extends B { public: def this {} override def f(x: $cParam): String = "C" }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    val c = new C(); val a: A = c; val b: B = c
           |    val n: Int? = 2
           |    return a.f(1) + b.f(1) + c.f(1) + b.f(n) + c.f(n)
           |  }
           |}
           |""".stripMargin,
        "BridgeChain.on", Array())

    it("dispatches a three-level chain `f(Int)`, `f(Int?)`, `f(Int)` to the last override") {
      assert(chain("Int", "Int?", "Int") == onion.tools.Shell.Success("CCCCC"))
    }

    it("dispatches a three-level chain `f(Int?)`, `f(Int)`, `f(Int?)` to the last override") {
      assert(chain("Int?", "Int", "Int?") == onion.tools.Shell.Success("CCCCC"))
    }

    // Differing only by `?` is the same slot, `override` or not: `override` is a check and
    // never decides which method runs (panel block 6 on #3051). Before, an unmarked `void`
    // or primitive-return method stayed an overload while a reference return overrode.
    def unmarked(baseParam: String, subParam: String, ret: String): onion.tools.Shell.Result = {
      def body(who: String) = if ret == "void" then s"""{ who = who + "$who" }""" else s"""{ who = who + "$who"; return "$who" }"""
      shell.run(
        s"""
           |class Base { public: var who: String; def this { who = "" } def dec(k: $baseParam): $ret ${body("Base")} }
           |class Up extends Base { public: def this {} def dec(k: $subParam): $ret ${body("Up")} }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    val u = new Up(); val b: Base = u
           |    u.dec(3); b.dec(3)
           |    return u.who
           |  }
           |}
           |""".stripMargin,
        "UnmarkedOverride.on", Array())
    }

    it("runs an unmarked `f(k: Int?): void` beside a concrete base `f(k: Int)`") {
      assert(unmarked("Int", "Int?", "void") == onion.tools.Shell.Success("UpUp"))
    }

    it("runs an unmarked `f(k: Int?): String` beside a concrete base `f(k: Int)`") {
      assert(unmarked("Int", "Int?", "String") == onion.tools.Shell.Success("UpUp"))
    }

    it("runs an unmarked `f(k: Int): String` beside a concrete base `f(k: Int?)`") {
      assert(unmarked("Int?", "Int", "String") == onion.tools.Shell.Success("UpUp"))
    }

    it("runs an unmarked `handle(code: Int?)` for an interface default `handle(code: Int)`") {
      val result = shell.run(
        """
          |interface H { def handle(code: Int): String = "default" }
          |class Mine conforms H { public: def this {} def handle(code: Int?): String = "Mine" }
          |class Test {
          |public:
          |  static def main(args: String[]): String { val h: H = new Mine(); return h.handle(1) }
          |}
          |""".stripMargin,
        "UnmarkedDefault.on", Array())
      assert(result == onion.tools.Shell.Success("Mine"))
    }

    // A `?` bridge over a final method made the JVM reject the class (panel block 6 on #3051).
    it("reports E0039 for `override def f(k: Int?)` over a final `f(k: Int)`") {
      val results = errors(
        """
          |class A { public: def this {} final def f(k: Int): void { println("A") } }
          |class B extends A { public: def this {} override def f(k: Int?): void { println("B") } }
          |""".stripMargin)
      assert(results.map(_._1).contains(Some("E0039")), s"got: $results")
    }

    it("reports E0039 for an unmarked `f(k: Int?): String` beside a final `f(k: Int): String`") {
      val results = errors(
        """
          |class A { public: def this {} final def f(k: Int): String = "A" }
          |class B extends A { public: def this {} def f(k: Int?): String = "B" }
          |""".stripMargin)
      assert(results.map(_._1).contains(Some("E0039")), s"got: $results")
    }

    it("reports E0039 for `setDaemon(on: Boolean?)` over the final JDK `Thread.setDaemon`") {
      val results = errors(
        """
          |class T extends Thread { public: def this {} override def setDaemon(on: Boolean?): void {} }
          |""".stripMargin)
      assert(results.map(_._1).contains(Some("E0039")), s"got: $results")
    }

    // The hint shows the base signature at the subclass's type arguments: following it
    // must compile, so a type variable never appears in it (panel block 6 on #3051).
    it("substitutes a generic base's return type in the E0068 hint") {
      val results = errors(
        """
          |class Box[T] { public: def this {} def put(x: T): T = x }
          |class SBox extends Box[String] { public: def this {} override def put(x: Int): String = "s" }
          |""".stripMargin)
      val e0068 = results.filter(_._1.contains("E0068")).map(_._2)
      assert(e0068.exists(m => m.contains("put(String): String") && !m.contains(": T")), s"got: $results")
    }

    it("substitutes a Java generic base's return type in the E0068 hint") {
      val results = errors(
        """
          |import { java.util.AbstractList }
          |class L extends AbstractList[String] {
          |public:
          |  def this {}
          |  def size(): Int = 0
          |  override def get(i: Long): String = ""
          |}
          |""".stripMargin)
      val e0068 = results.filter(_._1.contains("E0068")).map(_._2)
      assert(e0068.exists(m => m.contains("get(Int): String") && !m.contains(": E")), s"got: $results")
    }

    it("reports E0068 for `override def f(k: JInteger)` over `f(k: Int)`") {
      val results = errors(program("Int", "JInteger", "void"))
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
    }

    it("names the base signature in E0068 when only the types differ") {
      val results = errors(program("Int", "JInteger", "void"))
      val e0068 = results.filter(_._1.contains("E0068")).map(_._2)
      assert(e0068.exists(m => m.contains("f(JInteger)") && m.contains("f(Int): void")), s"got: $results")
    }

    it("reports E0068 for a boxed override of a primitive Java method") {
      val results = errors(
        """
          |import { java.io.ByteArrayOutputStream }
          |class L extends ByteArrayOutputStream {
          |public:
          |  def this {}
          |  override def write(b: JInteger): void {}
          |}
          |""".stripMargin)
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
    }
  }
}
