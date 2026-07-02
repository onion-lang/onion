package onion.compiler.tools

import onion.tools.Shell

/**
 * Type classes stage 1: `instance Trait[Type] { ... }` parses and is lowered to a
 * public class implementing the trait interface (name mangled from the trait
 * application, e.g. Numeric[Integer] -> `Numeric$$Integer`). Registration,
 * coherence, and the dictionary singleton come in later stages.
 */
class TypeClassInstanceParsingSpec extends AbstractShellSpec {
  private val numeric = "trait Numeric[T] { def zero(): T\n def plus(a: T, b: T): T }\n"
  describe("instance declarations") {
    it("parse and compile alongside a program that runs") {
      assert(Shell.Success("ok") == shell.run(
        numeric + "instance Numeric[Integer] { def zero(): Integer = 0\n def plus(a: Integer, b: Integer): Integer { return a + b } }\ndef main(args: String[]): String { return \"ok\" }", "None", Array()))
    }
    it("lower to a well-formed class usable through the trait interface") {
      assert(Shell.Success(7) == shell.run(
        numeric + "instance Numeric[Integer] { def zero(): Integer = 0\n def plus(a: Integer, b: Integer): Integer { return a + b } }\ndef useIt(n: Numeric[Integer]): Integer { return n.plus(n.zero(), 7) }\ndef main(args: String[]): Int { return useIt(new `Numeric$$Integer`()) }", "None", Array()))
    }
    it("allow two distinct instances of the same trait (distinct mangled classes)") {
      assert(Shell.Success("<a>#1") == shell.run(
        "trait Show[T] { def show(x: T): String }\ninstance Show[String] { def show(x: String): String { return \"<\" + x + \">\" } }\ninstance Show[Integer] { def show(x: Integer): String { return \"#\" + x } }\ndef main(args: String[]): String { return (new `Show$$String`()).show(\"a\") + (new `Show$$Integer`()).show(1) }", "None", Array()))
    }
  }
}
