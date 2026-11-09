package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Issue #2664: a class with no constructor of its own calls `super()`. When the superclass has no
 * accessible no-arg constructor, that is E0021 (or E0013 for a private one) at the class, as the
 * explicit `def this { }` already reports, not a NoSuchMethodError when `new C()` runs.
 */
class ImplicitSuperConstructorSpec extends AnyFunSpec {

  private def errorCodes(source: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "S.on")))
      .diagnostics.errors.flatMap(_.errorCode).toSeq
  }

  describe("implicit super() of a class without a constructor") {
    it("rejects extends A when every parameter of A's only constructor has a default (E0021)") {
      val codes = errorCodes(
        """class A {
          |public:
          |  def this(a: Int = 1, b: Int = 2) { println("A" + a + b) }
          |}
          |class C extends A {
          |}
          |new C()
          |""".stripMargin)
      assert(codes == Seq("E0021"), s"codes: $codes")
    }

    it("rejects extends A when A's only constructor needs an argument (E0021)") {
      val codes = errorCodes(
        """class A {
          |public:
          |  def this(a: Int) { }
          |}
          |class C extends A {
          |}
          |""".stripMargin)
      assert(codes == Seq("E0021"), s"codes: $codes")
    }

    it("rejects extends A when A's no-arg constructor is private (E0013)") {
      val codes = errorCodes(
        """class A {
          |  def this { }
          |}
          |class C extends A {
          |}
          |""".stripMargin)
      assert(codes == Seq("E0013"), s"codes: $codes")
    }

    it("accepts extends A when A has a no-arg constructor, public or protected") {
      assert(errorCodes(
        """class A {
          |public:
          |  def this { }
          |  def this(a: Int) { }
          |}
          |class B {
          |protected:
          |  def this { }
          |}
          |class C extends A { }
          |class D extends B { }
          |class E extends Object { }
          |""".stripMargin).isEmpty)
    }

    it("accepts extends A for a class with an implicit constructor and a Java superclass with a no-arg one") {
      assert(errorCodes("class C extends java.util.ArrayList[String] { }\n").isEmpty)
    }
  }
}
