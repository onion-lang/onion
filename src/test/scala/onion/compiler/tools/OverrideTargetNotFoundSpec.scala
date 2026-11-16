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

    it("reports E0068 for `override def f(k: JInteger)` over `f(k: Int)`") {
      val results = errors(program("Int", "JInteger", "void"))
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
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
