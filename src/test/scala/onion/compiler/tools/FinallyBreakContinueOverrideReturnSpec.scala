package onion.compiler.tools

import onion.tools.Shell

/**
 * `FinallyOnReturnSpec` already locks in that a `finally` block's own `return` overrides
 * the try body's `return` (JLS 14.20.2). The same "finally's own abrupt completion wins"
 * rule applies just as much when the finally exits via `break`/`continue` instead of
 * `return` -- unwinding a `return`/`throw` from an enclosing loop's try body. This locks
 * that mirror-image case in explicitly.
 */
class FinallyBreakContinueOverrideReturnSpec extends AbstractShellSpec {
  it("lets a finally break override the try return") {
    assert(Shell.Success(99) == shell.run(
      "def f(): Int { while true { try { return 1 } finally { break } }\n return 99 }\ndef main(args: String[]): Int { return f() }",
      "None", Array()))
  }

  it("lets a finally break override a throw from the try") {
    assert(Shell.Success(99) == shell.run(
      "def f(): Int { while true { try { throw new RuntimeException(\"x\") } finally { break } }\n return 99 }\ndef main(args: String[]): Int { return f() }",
      "None", Array()))
  }

  it("lets a finally continue override the try return, resuming the loop") {
    assert(Shell.Success(3) == shell.run(
      "def f(): Int { var i = 0\n while i < 3 { i = i + 1\n try { return 1 } finally { continue } }\n return i }\ndef main(args: String[]): Int { return f() }",
      "None", Array()))
  }
}
