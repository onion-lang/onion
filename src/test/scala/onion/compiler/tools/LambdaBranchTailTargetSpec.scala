package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import onion.tools.Shell

import java.io.StringReader

/**
 * Issue #3243: a lambda passed to a generic method whose last expression is an
 * `if`/`else` (or `select`, `try`) with `Option[String]` and `Option[Int]` branches
 * joined to the raw `Option`, which fit the call's target `Option[Int]`, so a
 * `String` sat in an `Option[Int]` and the program threw ClassCastException later.
 */
class LambdaBranchTailTargetSpec extends AbstractShellSpec {

  private val prelude =
    """import { onion.Option; java.util.List; }
      |def ch2[U](f: () -> U): U = f()
      |val tail: Option[Int] = Option::none()
      |val other: Option[String] = Option::some("str")
      |val b = 1 < 2
      |""".stripMargin

  /** Error codes and their lines, as `code@line`. */
  private def errors(body: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(prelude + body), "B.on")))
    result.allErrors.map(e => s"${e.errorCode.getOrElse("?")}@${if (e.location == null) -1 else e.location.line}").toSeq
  }

  private def run(body: String, name: String) = shell.run(prelude + body, name, Array())

  describe("a branching last expression in a lambda is checked against the call's target (#3243)") {
    it("if/else with an Option[String] branch, where the target pins Option[Int] (the issue's repro)") {
      assert(errors("val c: Option[Int] = ch2(() -> { if b { other } else { tail } })\n") == Seq("E0000@6"))
    }

    it("the lambda written without braces") {
      assert(errors("val c: Option[Int] = ch2(() -> if b { tail } else { other })\n") == Seq("E0000@6"))
    }

    it("select and try") {
      assert(errors(
        """val c: Option[Int] = ch2(() -> {
          |  select 1 {
          |  case 1: tail
          |  else: other
          |  }
          |})
          |""".stripMargin) == Seq("E0000@6"))
      assert(errors(
        """val c: Option[Int] = ch2(() -> {
          |  try { other } catch e: Exception { tail }
          |})
          |""".stripMargin) == Seq("E0000@6"))
    }

    it("list branches") {
      assert(errors(
        """val xs: List[Int] = [1]
          |val ss: List[String] = ["s"]
          |val c: List[Int] = ch2(() -> { if b { xs } else { ss } })
          |""".stripMargin) == Seq("E0000@8"))
    }

    it("a null branch into a nullable target is not a W0012") {
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
      val result = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(
        () => new StringReader(prelude + "val c: Option[Int]? = ch2(() -> { if b { null } else { tail } })\nprintln(c)\n"), "N.on")))
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(result.diagnostics.warnings.filter(_.category.code == "W0012").isEmpty)
    }

    it("well-typed branches still compile and run") {
      val result = run(
        """val some: Option[Int] = Option::some(3)
          |val c: Option[Int] = ch2(() -> { if b { some } else { tail } })
          |val d = ch2(() -> { if b { some } else { tail } })
          |val o: Object = ch2(() -> { if b { other } else { tail } })
          |def main(args: String[]): String = "" + (c.get() + 1) + " " + d + " " + o
          |""".stripMargin,
        "BranchTailOk.on"
      )
      assert(Shell.Success("4 Some[value=3] Some[value=str]") == result)
    }
  }
}
