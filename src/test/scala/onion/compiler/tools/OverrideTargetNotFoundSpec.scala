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
}
