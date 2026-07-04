package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression tests for issue #257: a `val` whose initializer is a type error
 * must still register its binding at the declared type, so later references
 * resolve instead of cascading a spurious E0002 ("local variable not found")
 * through the rest of the block.
 */
class TypeMismatchNoCascadeSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 100)

  private def errorsOf(code: String): Seq[String] = {
    val result = new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )
    result.allErrors.map(_.message)
  }

  describe("type-mismatched val binding (issue #257)") {

    it("does not cascade E0002 to downstream references") {
      val errors = errorsOf(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = "wrong"
          |    val y: Int = x + 1
          |    val z: Int = y + 2
          |    IO::println(z)
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      // Exactly one error: the real type mismatch on `val x`. No spurious
      // "variable not found" (E0002) for x, y, or z.
      assert(errors.length == 1,
        s"expected exactly one error, got ${errors.length}: $errors")
      assert(!errors.exists(e => e.contains("見つかりません") || e.contains("not found")),
        s"expected no 'variable not found' cascade, got: $errors")
    }

    it("still reports a genuinely undefined variable") {
      val errors = errorsOf(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val y: Int = undefinedVar + 1
          |    IO::println(y)
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("undefinedVar")),
        s"expected an undefined-variable error, got: $errors")
    }

    it("still reports a duplicate declaration after a failed initializer") {
      val errors = errorsOf(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = "wrong"
          |    val x: Int = 5
          |    IO::println(x)
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(e => e.contains("重複") || e.contains("uplicat")),
        s"expected a duplicate-declaration error, got: $errors")
    }

    it("leaves a correct program unaffected") {
      val errors = errorsOf(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 5
          |    val y: Int = x + 1
          |    val z: Int = y + 2
          |    return z
          |  }
          |}
        """.stripMargin
      )
      assert(errors.isEmpty, s"expected no errors, got: $errors")
    }
  }
}
