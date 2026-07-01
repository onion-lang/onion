package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for single static method imports: import { Class::method }.
 */
class SingleStaticImportSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(source: String): Unit = {
    val compiler = new OnionCompiler(newConfig)
    val result = compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Test.on")))
    assert(result.diagnostics.errors.isEmpty, s"compilation errors: ${result.diagnostics.errors.mkString(", ")}")
  }

  describe("Single static method imports") {

    it("imports a single static method") {
      compile(
        """import { java.lang.Math::max; }
          |println(max(10, 20))
          |""".stripMargin
      )
    }

    it("imports multiple static methods from the same class") {
      compile(
        """import { java.lang.Math::max; java.lang.Math::min; }
          |println(max(10, 20))
          |println(min(10, 20))
          |""".stripMargin
      )
    }

    it("mixes single-method and class-level static imports") {
      compile(
        """import { java.lang.Math; java.lang.Math::max; }
          |println(max(1, 2))
          |println(min(1, 2))
          |""".stripMargin
      )
    }

    it("allows single-method import together with type imports") {
      compile(
        """import { java.util.ArrayList; java.lang.Math::abs; }
          |val xs = new ArrayList[Int]()
          |xs.add(abs(-5))
          |""".stripMargin
      )
    }
  }
}
