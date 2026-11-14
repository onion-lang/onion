package onion.compiler.tools

import onion.tools.Shell

/**
 * `super.f((s) -> ...)`: an untyped lambda takes its parameter types from the superclass
 * method, as `this.f(...)` does (#2674, was E0052).
 */
class SuperUntypedLambdaSpec extends AbstractShellSpec {

  private def run(code: String): Shell.Result = shell.run(code, "None", Array())

  describe("super call with an untyped lambda argument (#2674)") {
    it("types the lambda from the superclass method, like this.f") {
      assert(Shell.Success("x!,y!") == run(
        "class A {\npublic:\n  def this {}\n  def apply(f: String -> String, s: String): String = f(s)\n}\n" +
        "class B extends A {\npublic:\n  def this {}\n" +
        "  def viaThis(): String = this.apply((s) -> s + \"!\", \"x\")\n" +
        "  def viaSuper(): String = super.apply((s) -> s + \"!\", \"y\")\n}\n" +
        "class Main { public: static def main(args: String[]): String { val b = new B(); return b.viaThis() + \",\" + b.viaSuper() } }"))
    }

    it("accepts a trailing lambda and delegates from an override") {
      assert(Shell.Success("[HI]") == run(
        "class A {\npublic:\n  def this {}\n  def run(s: String, f: String -> String): String = f(s)\n}\n" +
        "class B extends A {\npublic:\n  def this {}\n" +
        "  override def run(s: String, f: String -> String): String = super.run(s) { t -> \"[\" + t.toUpperCase() + \"]\" }\n}\n" +
        "class Main { public: static def main(args: String[]): String { return new B().run(\"hi\", (t) -> t) } }"))
    }

    it("works inside a closure") {
      assert(Shell.Success("Z!") == run(
        "class A {\npublic:\n  def this {}\n  def apply(f: String -> String, s: String): String = f(s)\n}\n" +
        "class B extends A {\npublic:\n  def this {}\n" +
        "  def go(): String { val k: () -> String = () -> super.apply((s) -> s + \"!\", \"Z\"); return k() }\n}\n" +
        "class Main { public: static def main(args: String[]): String { return new B().go() } }"))
    }
  }
}
