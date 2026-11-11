package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Issue #2662: `extends A(1)` resolving to a private constructor of `A` is E0013, as
 * `new A(1)` is, instead of compiling and failing with IllegalAccessError at run time.
 * A `protected:` or `public:` constructor stays reachable from a subclass.
 */
class SuperConstructorAccessSpec extends AnyFunSpec {

  private def errorCodes(source: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "S.on")))
      .diagnostics.errors.flatMap(_.errorCode).toSeq
  }

  describe("super constructor access") {
    it("rejects extends A(1) when A's constructor is private (E0013)") {
      val codes = errorCodes(
        """class A {
          |  def this(a: Int) { }
          |}
          |class C extends A(1) { }
          |""".stripMargin)
      assert(codes == Seq("E0013"), s"codes: $codes")
    }

    it("rejects a primary constructor's extends A(n) when A's constructor is private (E0013)") {
      val codes = errorCodes(
        """class A {
          |  def this(a: Int) { }
          |}
          |class C(n: Int) extends A(n)
          |""".stripMargin)
      assert(codes == Seq("E0013"), s"codes: $codes")
    }

    it("rejects named super arguments, extends A(b = 10), when A's constructor is private (E0013)") {
      val codes = errorCodes(
        """class A { def this(a: Int = 3, b: Int = 4) { println(a + b) } }
          |class C extends A(b = 10) { }
          |new C()
          |""".stripMargin)
      assert(codes == Seq("E0013"), s"codes: $codes")
    }

    it("accepts named super arguments to a protected constructor") {
      val codes = errorCodes(
        """class A {
          |protected:
          |  def this(a: Int = 3, b: Int = 4) { println(a + b) }
          |}
          |class C extends A(b = 10) { }
          |new C()
          |""".stripMargin)
      assert(codes.isEmpty, s"codes: $codes")
    }

    it("accepts protected and public constructors") {
      val codes = errorCodes(
        """class A {
          |protected:
          |  def this(a: Int) { }
          |public:
          |  def this(a: String) { }
          |}
          |class C extends A(1) { }
          |class D extends A("x") { }
          |""".stripMargin)
      assert(codes.isEmpty, s"codes: $codes")
    }
  }
}
