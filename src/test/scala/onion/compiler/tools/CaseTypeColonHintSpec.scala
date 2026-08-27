package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java/Scala-style type-pattern case, `case s: String:`. Onion's `select` uses `is`
 * for a type test (`case s is String:`); a colon after the binding name is never valid
 * there, so the parser consumes `s` as an ordinary value pattern and trips several
 * characters later, on the *second* colon that introduces the branch body -- never
 * mentioning `is`.
 */
class CaseTypeColonHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java/Scala-style type-pattern case") {
    it("hints at `is` for `case s: String:`") {
      val msgs = messages(
        """
          |class Sample {
          |public:
          |  def describe(x: Object): String = {
          |    select x {
          |    case s: String:
          |      return s
          |    else:
          |      return "other"
          |    }
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("is"), s"expected a hint mentioning `is`, got: $msgs")
      assert(msgs.contains("case s is String"), s"expected the hint's example, got: $msgs")
    }

    it("does not fire for the correct `case s is String:` form") {
      val msgs = messages(
        """
          |class Sample {
          |public:
          |  def describe(x: Object): String = {
          |    select x {
          |    case s is String:
          |      return s
          |    else:
          |      return "other"
          |    }
          |  }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `case s is String:` form, got: $msgs")
    }
  }
}
