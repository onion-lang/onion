package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A record body that declares `def a(): Int` for a component `a` used to compile and then
 * fail to load with a JVM `ClassFormatError` (duplicate method). It is E0010 now (#2396).
 */
class RecordExplicitAccessorSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private def codes(src: String): Seq[Option[String]] = errors(src).map(_.errorCode)

  describe("a record body method named like a component") {
    it("is a duplicate method error, not a ClassFormatError at load") {
      val c = codes(
        """record R(a: Int) {
          |public:
          |  def a(): Int = 42
          |}
          |println(new R(1).a())
          |""".stripMargin)
      assert(c.contains(Some("E0010")))
    }

    it("covers the generic case the issue names, a String-bounded toString component") {
      val c = codes(
        """record G[T extends String](toString: T)
          |println(new G[String]("x").toString())
          |""".stripMargin)
      assert(c.contains(Some("E0010")))
    }

    it("is E0010 for a static method or one with another return type") {
      val c = codes(
        """record R(a: Int, b: Int) {
          |public:
          |  static def a(): Int = 42
          |  def b(): String = "s"
          |}
          |println(new R(1, 2))
          |""".stripMargin)
      assert(c.count(_ == Some("E0010")) == 2, c)
    }

    it("names the component and its accessor in the message") {
      val errs = errors(
        """record R(a: Int) {
          |public:
          |  def a(): Int = 42
          |}
          |println(new R(1).a())
          |""".stripMargin)
      assert(errs.map(_.errorCode) == Seq(Some("E0010")), errs)
      val msg = errs.head.message
      // Locale-independent: the component's declaration and its accessor appear in en and ja.
      assert(msg.contains("a: Int"), msg)
      assert(msg.contains("a(): Int"), msg)
      assert(errs.head.location.line == 3, errs.head.location)
    }

    it("still allows a method with parameters, or a different name") {
      val c = codes(
        """record R(a: Int) {
          |public:
          |  def a(x: Int): Int = x
          |  def b(): Int = a()
          |}
          |println(new R(1).b())
          |""".stripMargin)
      assert(c.isEmpty)
    }
  }
}
