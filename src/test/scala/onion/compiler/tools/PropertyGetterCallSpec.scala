package onion.compiler.tools

import onion.tools.Shell

/**
 * A zero-arg call to a name with no matching method resolves to a bean-property
 * getter, so `e.message()` works like `e.message` (parens optional on property
 * accessors, matching no-arg methods). A user extension method still wins over the
 * getter (resolution order: method > extension > getter).
 */
class PropertyGetterCallSpec extends AbstractShellSpec {
  it("resolves e.message() to getMessage()") {
    assert(Shell.Success("boom") == shell.run(
      "def main(args: String[]): String { try { throw new Exception(\"boom\") } catch e: Exception { return e.message() } }", "None", Array()))
  }
  it("still allows the parenless property form") {
    assert(Shell.Success("boom") == shell.run(
      "def main(args: String[]): String { try { throw new Exception(\"boom\") } catch e: Exception { return e.message } }", "None", Array()))
  }
  it("lets a user extension method win over a same-named getter") {
    assert(Shell.Success("user:a") == shell.run(
      "extension List[String] { def first(): String { return \"user:\" + this.get(0) } }\ndef main(args: String[]): String { val xs = new java.util.ArrayList[String]()\n xs.add(\"a\")\n return xs.first() }", "None", Array()))
  }
  it("still reports a clean error for a truly unknown method") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val o = new Object()\n IO::println(o.nope()) }", "None", Array()))
  }
}
