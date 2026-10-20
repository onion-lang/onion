package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, CompilationOutcome}
import onion.tools.Shell

import java.io.StringReader

/**
 * #2249 review: the #2251/#2248 fix (a bare, parens-less reference to a static sibling
 * method resolves instead of crashing with IncompatibleClassChangeError) only covers the
 * implicit/explicit *self* receiver. An explicit non-self receiver (`expr.staticMethod`,
 * no parens) must still be rejected the same way the parens form (`expr.staticMethod()`)
 * already is -- E0019 -- rather than silently calling the static method and dropping the
 * receiver expression's own evaluation (and any side effect it has).
 */
class StaticMethodViaInstanceSelectionSpec extends AbstractShellSpec {
  private def newConfig: CompilerConfig = CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compile(Seq(new StreamInputSource(() => new StringReader(code), "Test.on")))

  describe("a bare (parens-less) static method access through an explicit non-self receiver") {
    it("is rejected (E0019), not silently resolved, so the receiver's side effect is never dropped") {
      val src =
        """
          |class C {
          |public:
          |  static def greet(): String = "hi"
          |  static def mk(): C { IO::println("side effect"); return new C() }
          |  static def main(args: String[]): void {
          |    IO::println(C::mk().greet)
          |  }
          |}
          |""".stripMargin
      compile(src) match {
        case CompilationOutcome.Failure(errors) =>
          val codes = errors.flatMap(_.errorCode)
          assert(codes.contains("E0019"), s"expected E0019 in $codes")
        case other => fail(s"expected a compile failure, got: $other")
      }
    }

    it("stays consistent with the explicit-parens form, which already reports E0019") {
      val src =
        """
          |class C {
          |public:
          |  static def greet(): String = "hi"
          |  static def mk(): C = new C()
          |  static def main(args: String[]): void {
          |    IO::println(C::mk().greet())
          |  }
          |}
          |""".stripMargin
      compile(src) match {
        case CompilationOutcome.Failure(errors) =>
          val codes = errors.flatMap(_.errorCode)
          assert(codes.contains("E0019"), s"expected E0019 in $codes")
        case other => fail(s"expected a compile failure, got: $other")
      }
    }

    it("rejects a null-typed receiver too, instead of printing the static result") {
      val src =
        """
          |class C {
          |public:
          |  static def greet(): String = "hi"
          |  static def main(args: String[]): void {
          |    val y: C? = null
          |    IO::println(y!!.greet)
          |  }
          |}
          |""".stripMargin
      compile(src) match {
        case CompilationOutcome.Failure(errors) =>
          val codes = errors.flatMap(_.errorCode)
          assert(codes.contains("E0019"), s"expected E0019 in $codes")
        case other => fail(s"expected a compile failure, got: $other")
      }
    }
  }

  describe("a bare (parens-less) INSTANCE method access through an explicit receiver (no regression)") {
    it("still evaluates the receiver and calls the instance method") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  def greet(): String = "hi"
          |  static def mk(): C { IO::println("side effect"); return new C() }
          |  static def main(args: String[]): String = mk().greet
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hi") == result)
    }
  }
}
