package onion.compiler.tools

import onion.tools.Shell

/**
 * Soundness/diagnostic fixes surfaced by a gap-probe: a qualified assignment to a
 * nonexistent field must be reported (it used to be a silent no-op), and a record
 * that leaves an interface's abstract method unimplemented must be rejected at
 * compile time (it used to throw AbstractMethodError at runtime).
 */
class SoundnessProbeFixesSpec extends AbstractShellSpec {
  describe("qualified assignment to a nonexistent field") {
    it("is a compile error, not a silent no-op") {
      assert(Shell.Failure(-1) == shell.run(
        "record Box(v: Int)\ndef main(args: String[]): void { val b = new Box(3)\n b.w = 5\n IO::println(\"x\") }", "None", Array()))
    }
  }
  describe("record implementing an interface") {
    it("is rejected when an abstract method is unimplemented") {
      assert(Shell.Failure(-1) == shell.run(
        "interface Describable { def describe(): String }\nrecord Point(x: Int, y: Int) <: Describable\ndef main(args: String[]): void { }", "None", Array()))
    }
    it("compiles when an accessor satisfies the interface method") {
      assert(Shell.Success("Alice") == shell.run(
        "interface Named { def name(): String }\nrecord Person(name: String) <: Named\ndef main(args: String[]): String { val n: Named = new Person(\"Alice\")\n return n.name() }", "None", Array()))
    }
  }
}
