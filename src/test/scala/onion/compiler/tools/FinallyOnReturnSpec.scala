package onion.compiler.tools

import onion.tools.Shell

/**
 * A `finally` block runs when the `try` (or a `catch`) exits via `return`. It used
 * to be skipped entirely on the return path -- a serious correctness bug (resource
 * cleanup and side effects in the finally were silently lost).
 */
class FinallyOnReturnSpec extends AbstractShellSpec {
  it("runs finally when the try returns a value") {
    assert(Shell.Success("finally ran|1") == shell.run(
      "def f(): Int { try { return 1 } finally { IO::print(\"finally ran|\") } }\ndef main(args: String[]): String { return \"\" + f() }", "None", Array()))
  }
  it("runs finally when a void try returns") {
    assert(Shell.Success("try|finally") == shell.run(
      "def g(): void { try { IO::print(\"try|\")\n return } finally { IO::print(\"finally\") } }\ndef main(args: String[]): String { g()\n return \"\" }", "None", Array()))
  }
  it("runs finally when a catch returns") {
    assert(Shell.Success("fin|9") == shell.run(
      "def f(): Int { try { throw new RuntimeException(\"x\") } catch e: Exception { return 9 } finally { IO::print(\"fin|\") } }\ndef main(args: String[]): String { return \"\" + f() }", "None", Array()))
  }
  it("still runs finally on normal completion") {
    assert(Shell.Success("try|finally") == shell.run(
      "def main(args: String[]): String { try { IO::print(\"try|\") } finally { IO::print(\"finally\") }\n return \"\" }", "None", Array()))
  }
}
