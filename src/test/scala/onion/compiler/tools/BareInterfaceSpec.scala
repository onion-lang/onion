package onion.compiler.tools

import onion.tools.Shell

/**
 * An interface may omit its body, like a class can (`class Empty` already worked).
 * `interface Marker`, `interface Marker;`, and `interface Marker <: Simple` all
 * parse now (a bare marker interface used to be a syntax error).
 */
class BareInterfaceSpec extends AbstractShellSpec {
  it("parses a bare marker interface") {
    assert(Shell.Success("ok") == shell.run(
      "interface Marker\nclass C <: Marker { public: def this{} }\ndef main(args: String[]): String { return \"ok\" }", "None", Array()))
  }
  it("parses a bare sealed interface with record subtypes") {
    assert(Shell.Success(9) == shell.run(
      "sealed interface Shape\nrecord Circle(r: Int) <: Shape\nrecord Square(s: Int) <: Shape\ndef area(sh: Shape): Int { return select sh { case Circle(r): r * r\n case Square(s): s * s } }\ndef main(args: String[]): Int { return area(new Circle(3)) }", "None", Array()))
  }
  it("parses a bare interface terminated with a semicolon") {
    assert(Shell.Success("ok") == shell.run(
      "interface Marker;\nclass C <: Marker { public: def this{} }\ndef main(args: String[]): String { return \"ok\" }", "None", Array()))
  }
  it("still parses an interface with a body") {
    assert(Shell.Success("hi") == shell.run(
      "interface Greet { def hi(): String }\nclass C <: Greet { public: def this{}\n def hi(): String = \"hi\" }\ndef main(args: String[]): String { return new C().hi() }", "None", Array()))
  }
}
