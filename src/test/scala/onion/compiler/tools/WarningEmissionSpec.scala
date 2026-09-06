package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Behavioral regression coverage for the three `WarningCategory` cases that are wired
 * into the compiler (`WarningReporter.unreachableCode`/`shadowedVariable`/
 * `discardedTopLevelStatements` are each called from production code) but had no test
 * compiling a program and checking the warning actually comes out. `UnderscoreUnusedWarningSpec`,
 * `NullToNonNullableSpec`, `SuspiciousInterpolationSpec`, `PlatformUnboxingWarningSpec` and
 * `IneffectiveTailRecursiveWarningSpec` already do this for the other wired codes
 * (W0001/W0006, W0012, W0013, W0015, W0016); this fills the W0003/W0005/W0014 gap.
 *
 * See `ReservedWarningCodeSpec` for the codes that are declared and documented but never
 * fired by any code path at all (W0002, W0004, W0007-W0011).
 */
class WarningEmissionSpec extends AnyFunSpec {

  private def compileWarnings(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  it("W0003: warns on a statement after a terminating return") {
    val result = compileWarnings(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): void {
        |    return
        |    IO::println("dead")
        |  }
        |}
        |""".stripMargin)
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    val unreachable = result.diagnostics.warnings.filter(_.category.code == "W0003")
    assert(unreachable.length == 1, s"expected 1 W0003, got: ${result.diagnostics.warnings.map(_.message)}")
  }

  it("W0003: does not warn when no statement follows a terminating return") {
    val result = compileWarnings(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    return args.length
        |  }
        |}
        |""".stripMargin)
    assert(!result.hasErrors)
    assert(result.diagnostics.warnings.forall(_.category.code != "W0003"))
  }

  it("W0005: warns when a nested block shadows an enclosing local") {
    val result = compileWarnings(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val x = 1
        |    if args.length == 0 {
        |      val x = 2
        |      IO::println(x)
        |    }
        |    return x
        |  }
        |}
        |""".stripMargin)
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    val shadowed = result.diagnostics.warnings.filter(_.category.code == "W0005")
    assert(shadowed.length == 1, s"expected 1 W0005, got: ${result.diagnostics.warnings.map(_.message)}")
  }

  it("W0005: does not warn for two sibling blocks each declaring their own local") {
    val result = compileWarnings(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    if args.length == 0 {
        |      val y = 1
        |      IO::println(y)
        |    } else {
        |      val y = 2
        |      IO::println(y)
        |    }
        |    return 0
        |  }
        |}
        |""".stripMargin)
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    assert(result.diagnostics.warnings.forall(_.category.code != "W0005"))
  }

  it("W0014: warns on bare top-level statements discarded because a top-level main exists") {
    val result = compileWarnings(
      """
        |def main(args: String[]): void {
        |  IO::println("hi")
        |}
        |IO::println("discarded")
        |""".stripMargin)
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    val discarded = result.diagnostics.warnings.filter(_.category.code == "W0014")
    assert(discarded.length == 1, s"expected 1 W0014, got: ${result.diagnostics.warnings.map(_.message)}")
  }

  it("W0014: does not warn when the only top-level declaration is main itself") {
    val result = compileWarnings(
      """
        |def main(args: String[]): void {
        |  IO::println("hi")
        |}
        |""".stripMargin)
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    assert(result.diagnostics.warnings.forall(_.category.code != "W0014"))
  }
}
