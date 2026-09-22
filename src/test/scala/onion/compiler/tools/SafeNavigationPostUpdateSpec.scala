package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * `obj?.field++`/`obj?.field--` (and the safe-indexing form `obj?[index]++`)
 * are rejected as a non-lvalue, the same as `obj?.field = value` already is --
 * safe navigation short-circuits to null at runtime, which has no sensible
 * meaning as an update target. Previously this fell through to the generic
 * numeric-operand check instead: the nullable-wrapped read type (e.g. `Int?`)
 * tripped `INCOMPATIBLE_OPERAND_TYPE` (E0001, "operator ++ is not applicable
 * for type Int?"), a confusing type-shaped message for what is actually the
 * same not-an-lvalue mistake as the already-fixed `obj?.field = value` case,
 * and with no hint pointing at a null check or dropping the `?.`/`?`.
 */
class SafeNavigationPostUpdateSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("Post-increment/decrement through safe navigation (obj?.field++)") {
    it("reports E0028 with the safe-navigation hint, not E0001, for post-increment") {
      val result = errors(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 0 }
          |}
          |def main: void {
          |  val b: Box? = new Box()
          |  b?.count++
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?.") })
      assert(!result.exists { case (code, _) => code.contains("E0001") })
    }

    it("reports E0028 with the safe-navigation hint for post-decrement") {
      val result = errors(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 0 }
          |}
          |def main: void {
          |  val b: Box? = new Box()
          |  b?.count--
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?.") })
    }
  }

  describe("Post-increment/decrement through safe indexing (obj?[index]++)") {
    it("reports E0028 with the safe-indexing hint, not E0001, for post-increment") {
      val result = errors(
        """
          |def main: void {
          |  val a: Int[]? = new Int[1]
          |  a?[0]++
          |}
        """.stripMargin)
      assert(result.exists { case (code, msg) => code.contains("E0028") && msg.contains("?[") })
      assert(!result.exists { case (code, _) => code.contains("E0001") })
    }
  }

  describe("Unaffected cases") {
    it("still increments a non-null-checked field normally") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 41 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val b = new Box()
          |    b.count++
          |    return b.count
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(onion.tools.Shell.Success(42) == result)
    }

    it("leaves the read form (obj?.field) unaffected") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  var count: Int
          |  def this { self.count = 42 }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Box? = new Box()
          |    return b?.count ?: -1
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(onion.tools.Shell.Success(42) == result)
    }
  }
}
