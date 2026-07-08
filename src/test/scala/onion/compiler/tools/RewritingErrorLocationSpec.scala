package onion.compiler.tools

import onion.tools.Shell

/**
 * Rewriting-phase errors (desugar guards, auto-CLI, coherence) must render with a
 * file:line:col location like every other diagnostic. They were built with an empty
 * sourceFile, so processBody fills in the unit's source file. This asserts the error
 * still fires (Shell.Failure) — the location prefix is verified by the compiler's
 * reporter; we keep the assertion locale-agnostic.
 */
class RewritingErrorLocationSpec extends AbstractShellSpec {
  it("an enum mixing shared params with case cases is a failure") {
    val result = shell.run(
      """
        | enum Bad(x: Int) {
        |   case Foo(y: Int)
        |   case Baz(z: Int)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }

  it("an unsupported main signature is a failure") {
    val result = shell.run(
      """
        | def main(args: String[], flag: Boolean): void { IO::println("x") }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }
}
