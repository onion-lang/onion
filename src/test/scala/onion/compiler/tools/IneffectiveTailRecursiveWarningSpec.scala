package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for W0016: `@TailRecursive` marks a mutual-recursion group, but
 * `MutualRecursionOptimization` only rewrites a group when every member is
 * private, shares one return type, and only tail-calls within the group
 * (`MutualRecursionOptimization.validateGroup`). Before this warning, a group
 * that failed that check compiled silently and only surfaced as a runtime
 * StackOverflowError on deep recursion — the annotation looked honored but
 * was not.
 */
class IneffectiveTailRecursiveWarningSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("W0016 ineffective @TailRecursive") {
    it("warns when the annotated group is not private") {
      val result = compileWarnings(
        """
          |class Parity {
          |public:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean {
          |    if n == 0 { return true }
          |    return isOdd(n - 1)
          |  }
          |  @TailRecursive
          |  def isOdd(n: Int): Boolean {
          |    if n == 0 { return false }
          |    return isEven(n - 1)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w16 = result.diagnostics.warnings.filter(_.category.code == "W0016")
      assert(w16.length == 2, s"expected 2 W0016 (one per method), got: ${result.diagnostics.warnings.map(_.message)}")
      assert(w16.forall(_.message.contains("must be private")))
    }

    it("does not warn when the private group is optimized successfully") {
      val result = compileWarnings(
        """
          |class Parity {
          |private:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean {
          |    if n == 0 { return true }
          |    return isOdd(n - 1)
          |  }
          |  @TailRecursive
          |  def isOdd(n: Int): Boolean {
          |    if n == 0 { return false }
          |    return isEven(n - 1)
          |  }
          |public:
          |  def check(n: Int): Boolean = isEven(n)
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(result.diagnostics.warnings.filter(_.category.code == "W0016").isEmpty)
    }

    it("warns when the group's parameter lists don't match") {
      val result = compileWarnings(
        """
          |class Parity {
          |private:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean {
          |    if n == 0 { return true }
          |    return isOdd(n - 1, 0)
          |  }
          |  @TailRecursive
          |  def isOdd(n: Int, extra: Int): Boolean {
          |    if n == 0 { return false }
          |    return isEven(n - 1)
          |  }
          |public:
          |  def check(n: Int): Boolean = isEven(n)
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w16 = result.diagnostics.warnings.filter(_.category.code == "W0016")
      assert(w16.length == 2, s"expected 2 W0016 (one per method), got: ${result.diagnostics.warnings.map(_.message)}")
      assert(w16.forall(_.message.contains("parameter")))
    }

    it("fails compilation under warnings-as-errors") {
      val result = compileWarnings(
        """
          |class Parity {
          |public:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean {
          |    if n == 0 { return true }
          |    return isOdd(n - 1)
          |  }
          |  @TailRecursive
          |  def isOdd(n: Int): Boolean {
          |    if n == 0 { return false }
          |    return isEven(n - 1)
          |  }
          |}
          |""".stripMargin,
        WarningLevel.Error)
      assert(result.hasErrors)
    }
  }
}
