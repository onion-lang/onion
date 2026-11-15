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
  describe("an override that matches a non-generic base method only through boxing (#2896)") {
    // A non-generic supertype gets no bridge, so `dec(Integer)` and `dec(int)` are two
    // JVM methods: the "override" would never run.
    def base(param: String) =
      s"""
         |class Base {
         |public:
         |  def this {}
         |  def dec(k: $param): void {}
         |}
         |""".stripMargin

    it("reports E0068 for `override def dec(k: Int?)` over `dec(k: Int)`") {
      val results = errors(base("Int") + "class Up extends Base {\npublic:\n  def this {}\n  override def dec(k: Int?): void {}\n}\n")
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
    }

    it("reports E0068 for `override def dec(k: Int)` over `dec(k: Int?)`") {
      val results = errors(base("Int?") + "class Up extends Base {\npublic:\n  def this {}\n  override def dec(k: Int): void {}\n}\n")
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
    }

    it("reports E0068 for `override def dec(k: JInteger)` over `dec(k: Int)`") {
      val results = errors(base("Int") + "class Up extends Base {\npublic:\n  def this {}\n  override def dec(k: JInteger): void {}\n}\n")
      assert(results.map(_._1).contains(Some("E0068")), s"got: $results")
    }

    it("reports E0068 for a primitive override of a boxed Java method") {
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

    it("still accepts an exact override, which runs") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  def this {}
          |  def dec(k: Int?): Int = 1
          |}
          |class Up extends Base {
          |public:
          |  def this {}
          |  override def dec(k: Int?): Int = 2
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { val b: Base = new Up(); return b.dec(3) }
          |}
          |""".stripMargin, "ExactOverride.on", Array())
      assert(onion.tools.Shell.Success(2) == result)
    }
  }
}
