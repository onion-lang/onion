package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/** `super.f()` on a bodyless method compiled, then threw AbstractMethodError (issue #2656). */
class SuperAbstractCallSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("super.f() where the superclass's f is abstract") {
    it("is E0115 at compile time") {
      val c = codes(
        """
          |abstract class Base {
          |public:
          |  abstract def f(): Int
          |}
          |class Child extends Base {
          |public:
          |  override def f(): Int = 2
          |  def g(): Int = super.f()
          |}
          |println(new Child().g())
          |""".stripMargin)
      assert(c == Seq(Some("E0115")), s"got: $c")
    }

    it("is E0115 when the abstract method comes from an interface the superclass leaves open") {
      val c = codes(
        """
          |interface I { def f(): Int }
          |abstract class Base conforms I { }
          |class Child extends Base {
          |public:
          |  override def f(): Int = 2
          |  def g(): Int = super.f()
          |}
          |println(new Child().g())
          |""".stripMargin)
      assert(c == Seq(Some("E0115")), s"got: $c")
    }

    it("still compiles when the superclass method has a body") {
      val c = codes(
        """
          |class Base {
          |public:
          |  def f(): Int = 1
          |}
          |class Child extends Base {
          |public:
          |  override def f(): Int = super.f() + 1
          |}
          |println(new Child().f())
          |""".stripMargin)
      assert(c.isEmpty, s"got: $c")
    }
  }
}
