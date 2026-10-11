package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `def f(): void = voidExpr` (the expression-body form, no `{ }`) always desugared to
 * `{ return voidExpr }`, which trips the same Java-style restriction that forbids
 * `return someVoidMethod();` even inside a void method -- a restriction meant for an
 * explicit `return` statement a user actually wrote, not for this sugar. A block-bodied
 * void method ending in the same call with no `return` (`def f(): void { voidExpr }`)
 * already worked, so the expression-body sugar was the one gap. Fixed by desugaring a
 * `void`-returning expression body to a bare statement (`{ voidExpr }`) instead of
 * `{ return voidExpr }`, in both the handwritten parser and the JavaCC grammar -- the same
 * change also lets a `void`-returning expression body discard a non-void value
 * (`def bar(): void = compute()`), matching what the identical block-bodied form
 * (`def bar(): void { compute() }`) already allowed.
 */
class VoidExpressionBodyVoidCallSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runReadingProperty(key: String, script: String): String = {
    System.clearProperty(key)
    shell.run(script, "None", Array())
    System.getProperty(key)
  }

  describe("a void-returning expression-body method whose body is itself a void call") {
    it("compiles and runs a top-level function (no class)") {
      val v = runReadingProperty(
        "test.voidExprBody.topLevel",
        """
          |def log(msg: String): void = System::setProperty("test.voidExprBody.topLevel", msg)
          |log("top-level-ok")
          |""".stripMargin
      )
      assert(v == "top-level-ok")
    }

    it("compiles and runs an instance method") {
      val v = runReadingProperty(
        "test.voidExprBody.instance",
        """
          |class Logger {
          |public:
          |  def log(msg: String): void = System::setProperty("test.voidExprBody.instance", msg)
          |}
          |new Logger().log("instance-ok")
          |""".stripMargin
      )
      assert(v == "instance-ok")
    }

    it("compiles and runs an interface default method") {
      val v = runReadingProperty(
        "test.voidExprBody.interfaceDefault",
        """
          |interface Logs {
          |  def log(msg: String): void = System::setProperty("test.voidExprBody.interfaceDefault", msg)
          |}
          |class Logger conforms Logs { }
          |new Logger().log("default-ok")
          |""".stripMargin
      )
      assert(v == "default-ok")
    }
  }

  describe("behavior that must stay unchanged") {
    it("now also accepts a void-returning expression body whose expression has a real value, discarding it " +
      "-- the same thing the identical block-bodied method (`{ compute() }`) already allowed") {
      val v = runReadingProperty(
        "test.voidExprBody.exprDiscard",
        """
          |class Foo {
          |public:
          |  def compute(): Int {
          |    System::setProperty("test.voidExprBody.exprDiscard", "expr-ok")
          |    return 42
          |  }
          |  def bar(): void = compute()
          |}
          |new Foo().bar()
          |""".stripMargin
      )
      assert(v == "expr-ok")
    }

    it("still rejects an explicit `return <void call>;` inside a void method's block body (E0020)") {
      val codes = errorCodes(
        """
          |class Foo {
          |public:
          |  def helper(): void = IO::println("x")
          |  def bar(): void {
          |    return helper()
          |  }
          |}
          |""".stripMargin
      )
      assert(codes.contains("E0020"), s"expected E0020 in $codes")
    }

    it("still runs a block-bodied void method that discards a non-void trailing expression") {
      val v = runReadingProperty(
        "test.voidExprBody.blockDiscard",
        """
          |class Foo {
          |public:
          |  def compute(): Int = 42
          |  def bar(): void { System::setProperty("test.voidExprBody.blockDiscard", "block-ok"); compute() }
          |}
          |new Foo().bar()
          |""".stripMargin
      )
      assert(v == "block-ok")
    }
  }
}
