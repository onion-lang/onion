package onion.compiler.tools

import onion.tools.Shell

/**
 * An empty `[]` / `[:]` next to typed siblings takes the siblings' type, so
 * `[[1, 2], []]` is a List[List[Int]] and `adj[1].add(5)` compiles (#2430).
 */
class EmptyLiteralSiblingTypeSpec extends AbstractShellSpec {
  private def main(body: String, ret: String = "Int"): String =
    s"def main(args: String[]): $ret {\n$body\n}"

  it("types an empty list after a typed sibling list") {
    assert(Shell.Success(3) == shell.run(
      main("val adj = [[1, 2], []]\nadj[1].add(5)\nreturn adj[0].size + adj[1].size"), "None", Array()))
  }
  it("types an empty list written before its sibling") {
    assert(Shell.Success(1) == shell.run(
      main("val z = [[], [1]]\nz[0].add(9)\nreturn z[0].size"), "None", Array()))
  }
  it("types an empty list value in a map literal") {
    assert(Shell.Success(7) == shell.run(
      main("val m = [\"a\": [1], \"b\": []]\nm[\"b\"].add(7)\nreturn m[\"b\"][0]"), "None", Array()))
  }
  it("types an empty map after a typed sibling map") {
    assert(Shell.Success(2) == shell.run(
      main("val q = [[:], [\"k\": 1]]\nq[0][\"x\"] = 2\nreturn q[0][\"x\"]"), "None", Array()))
  }
  it("keeps evaluation order of the elements") {
    assert(Shell.Success("ab") == shell.run(
      main("val sb = new StringBuilder()\nval xs = [f(sb, \"a\"), [], f(sb, \"b\")]\nreturn sb.toString()", "String") +
        "\ndef f(sb: StringBuilder, s: String): List[String] { sb.append(s)\n return [s] }", "None", Array()))
  }
}
