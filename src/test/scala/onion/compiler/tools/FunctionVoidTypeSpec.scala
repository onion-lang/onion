package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for `() -> void` / `() -> Unit` function types.
 */
class FunctionVoidTypeSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(source: String): Unit = {
    val compiler = new OnionCompiler(newConfig)
    val result = compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Test.on")))
    assert(result.diagnostics.errors.isEmpty, s"compilation errors: ${result.diagnostics.errors.mkString(", ")}")
  }

  describe("Function types with void/Unit return") {

    it("accepts () -> Unit as a parameter type") {
      compile(
        """def run(block: () -> Unit): void { block.call() }
          |run(() -> { println("ok") })
          |""".stripMargin
      )
    }

    it("accepts () -> void as a parameter type") {
      compile(
        """def run(block: () -> void): void { block.call() }
          |run(() -> { println("ok") })
          |""".stripMargin
      )
    }

    it("accepts (Int) -> Unit as a parameter type") {
      compile(
        """def run(block: (Int) -> Unit): void { block.call(42) }
          |run((x: Int) -> { println(x) })
          |""".stripMargin
      )
    }

    it("accepts () -> Unit as a variable type") {
      compile(
        """val f: () -> Unit = () -> { println("ok") }
          |f.call()
          |""".stripMargin
      )
    }
  }
}
