package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `self`/`this` is bound as an ordinary local inside an `extension T? { ... }`
 * method (the one place it can be nullable), so a null check on it should
 * smart-cast it like any other immutable local -- both as a guard-clause
 * early return and as an if/else branch narrowing.
 */
class NullableSelfNarrowingSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(source: String): Unit = {
    val compiler = new OnionCompiler(newConfig)
    val result = compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Test.on")))
    assert(result.diagnostics.errors.isEmpty, s"compilation errors: ${result.diagnostics.errors.mkString(", ")}")
  }

  describe("null narrowing of self/this in a nullable-receiver extension") {

    it("narrows self after a guard-clause early return (if self == null { return })") {
      compile(
        """extension String? {
          |  def orEmpty(): String {
          |    if self == null { return "" }
          |    return self
          |  }
          |}
          |""".stripMargin
      )
    }

    it("narrows self after a guard-clause early return written with this") {
      compile(
        """extension String? {
          |  def orEmpty2(): String {
          |    if this == null { return "" }
          |    return this
          |  }
          |}
          |""".stripMargin
      )
    }

    it("narrows self in the then-branch of an if/else on self != null") {
      compile(
        """extension String? {
          |  def describe(): String {
          |    if self != null {
          |      return self
          |    } else {
          |      return "none"
          |    }
          |  }
          |}
          |""".stripMargin
      )
    }
  }
}
