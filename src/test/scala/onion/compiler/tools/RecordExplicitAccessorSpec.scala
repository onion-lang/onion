package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A record body that declares `def a(): Int` for a component `a` used to compile and then
 * fail to load with a JVM `ClassFormatError` (duplicate method). It is E0010 now (#2396).
 */
class RecordExplicitAccessorSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

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
