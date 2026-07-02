package onion.compiler.tools

import onion.tools.Shell

/**
 * Type classes stage 1: coherence -- at most one instance per (trait, type) in a
 * compilation unit. A duplicate is rejected; distinct type arguments are
 * unaffected. (The message itself is a clear "already defined" text rather than a
 * leaked internal class name; verified manually via the CLI.)
 */
class TypeClassCoherenceSpec extends AbstractShellSpec {
  describe("instance coherence") {
    it("rejects two instances for the same trait application") {
      assert(Shell.Failure(-1) == shell.run(
        "trait Numeric[T] { def zero(): T }\ninstance Numeric[Integer] { def zero(): Integer = 0 }\ninstance Numeric[Integer] { def zero(): Integer = 1 }\ndef main(args: String[]): void { IO::println(\"x\") }", "None", Array()))
    }
    it("allows distinct type arguments of the same trait") {
      assert(Shell.Success("ok") == shell.run(
        "trait Show[T] { def show(x: T): String }\ninstance Show[String] { def show(x: String): String = x }\ninstance Show[Integer] { def show(x: Integer): String { return \"\" + x } }\ndef main(args: String[]): String { return (new `Show$$String`()).show(\"ok\") }", "None", Array()))
    }
    it("allows distinct primitive vs boxed type arguments") {
      assert(Shell.Success("d") == shell.run(
        "trait Numeric[T] { def zero(): T }\ninstance Numeric[Integer] { def zero(): Integer = 0 }\ninstance Numeric[Int] { def zero(): Int = 0 }\ndef main(args: String[]): String { return \"d\" }", "None", Array()))
    }
  }
}
