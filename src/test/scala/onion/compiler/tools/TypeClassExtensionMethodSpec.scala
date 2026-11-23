package onion.compiler.tools

import onion.tools.Shell

/**
 * #3223: an extension method with a `[T: Trait]` type parameter gets its dictionary in its
 * own trailing slot. Before, the call site injected the dictionary into the argument list
 * before filling defaults, so it overwrote the last real argument and the dictionary slot
 * kept its null default: `"a".k(3)` printed the instance object, and a body that used the
 * constraint failed at run time.
 */
class TypeClassExtensionMethodSpec extends AbstractShellSpec {
  private val prelude =
    "trait Show[T] { def show(x: T): String }\n" +
    "instance Show[Integer] { def show(x: Integer): String = \"I\" + x }\n" +
    "instance Show[String] { def show(x: String): String = \"S\" + x }\n"

  private def run(ext: String, body: String): Shell.Result =
    shell.run(prelude + ext + "\ndef main(args: String[]): String {\n" + (if (body.contains("return")) body else "return " + body) + "\n}", "None", Array())

  describe("a [T: Trait] extension method (#3223)") {
    it("keeps its argument when the body does not use the constraint") {
      assert(Shell.Success("ak3") == run("extension String { def k[T: Show](x: T): String = this + \"k\" + x }", "\"a\".k(3)"))
    }
    it("receives the instance when the body uses the constraint") {
      assert(Shell.Success("aI3") == run("extension String { def k[T: Show](x: T): String = this + Show[T]::show(x) }", "\"a\".k(3)"))
    }
    it("picks the instance from the argument's type") {
      assert(Shell.Success("aSz") == run("extension String { def k[T: Show](x: T): String = this + Show[T]::show(x) }", "\"a\".k(\"z\")"))
    }
    it("keeps every argument and an omitted default") {
      val ext = "extension String { def k[T: Show](x: T, sep: String = \"-\"): String = this + sep + Show[T]::show(x) }"
      assert(Shell.Success("a-I3") == run(ext, "\"a\".k(3)"))
      assert(Shell.Success("a+I3") == run(ext, "\"a\".k(3, \"+\")"))
    }
    it("works through a safe call") {
      val ext = "extension String { def k[T: Show](x: T): String = this + Show[T]::show(x) }"
      assert(Shell.Success("aI3") == run(ext, "val s: String? = \"a\"\n return s?.k(3) ?: \"none\""))
    }
  }
}
