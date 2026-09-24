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

    it("warns instead of crashing when the group reads an instance field via `this`") {
      // Regression test (I0000): a private @TailRecursive group is otherwise eligible
      // for the state-machine rewrite (all private, same return type, same parameter
      // list, tail-calls only within the group) but reads an instance field. The
      // generated state machine method is always `private static`, so the field access
      // that survives verbatim into its body used to crash BytecodeGeneration with
      // "no 'this' pointer within static method" instead of compiling. It must now fall
      // back to the ordinary (non-tail-optimized) methods and report W0016 instead.
      val result = compileWarnings(
        """
          |class Accumulator {
          |private:
          |  val base: Int
          |public:
          |  def this(base: Int) { this.base = base }
          |private:
          |  @TailRecursive
          |  def stateA(n: Int, acc: Int): Int {
          |    if n <= 0 { return acc }
          |    return stateB(n - 1, acc + this.base)
          |  }
          |  @TailRecursive
          |  def stateB(n: Int, acc: Int): Int {
          |    if n <= 0 { return acc }
          |    return stateA(n - 1, acc - this.base)
          |  }
          |public:
          |  def run(n: Int): Int = stateA(n, 0)
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"expected a clean compile, got: ${result.allErrors.map(_.message)}")
      assert(result.diagnostics.errors.filter(_.code.contains("I0000")).isEmpty,
        "must not crash with an internal compiler error (I0000)")
      val w16 = result.diagnostics.warnings.filter(_.category.code == "W0016")
      assert(w16.length == 2, s"expected 2 W0016 (one per method), got: ${result.diagnostics.warnings.map(_.message)}")
      assert(w16.forall(_.message.contains("this")))
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
