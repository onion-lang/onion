package onion.compiler.tools

import onion.tools.Shell

/** #2359: cases after a guarded case were emitted twice, so a lambda in one failed JVM verification. */
class GuardedCaseClosureSpec extends AbstractShellSpec {
  private val prelude = "record P(a: Int, b: Int)\n"

  it("#2359: a lambda in the case after a guarded destructuring case runs") {
    assert(Shell.Success(3) == shell.run(
      prelude +
        "def f(p: P): Int = select p {\n" +
        "  case P(a, b) when a == 0: 0\n" +
        "  case P(a, b) when a > 0: { val g = () -> a; g() }\n" +
        "  else: -1\n" +
        "}\n" +
        "def main(args: String[]): Int { return f(new P(3, 0)) }", "None", Array()))
  }

  it("#2359: a failed guard falls through to the next case, then to else") {
    def run(x: Int): Shell.Result = shell.run(
      prelude +
        "def f(p: P): Int = select p {\n" +
        "  case P(a, b) when a == 0: 100\n" +
        "  case P(a, b) when a > 5: { val g = () -> a; g() }\n" +
        "  case P(a, b): { val g = () -> b; g() }\n" +
        "}\n" +
        "def main(args: String[]): Int { return f(new P(" + x + ", 7)) }", "None", Array())
    assert(Shell.Success(100) == run(0))
    assert(Shell.Success(9) == run(9))
    assert(Shell.Success(7) == run(2))
  }
}
