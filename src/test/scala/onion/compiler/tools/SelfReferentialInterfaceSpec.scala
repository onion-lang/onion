package onion.compiler.tools

import onion.tools.Shell

/**
 * A class may implement a generic interface with itself as the type argument
 * (`class Ver <: Comparable[Ver]`), the standard natural-ordering pattern. It used
 * to fail with E0000 because the type-argument bound check walked the class's not-yet
 * established supertype chain and wrongly rejected it against the Object bound.
 */
class SelfReferentialInterfaceSpec extends AbstractShellSpec {
  it("implements Comparable with itself as the type argument") {
    assert(Shell.Success(-2) == shell.run(
      "class Ver <: Comparable[Ver] { public: val n: Int\n def this(n: Int) { this.n = n }\n def compareTo(o: Ver): Int = n - o.n }\ndef main(args: String[]): Int { return new Ver(3).compareTo(new Ver(5)) }", "None", Array()))
  }
  it("sorts a self-Comparable class through Collections.sort") {
    assert(Shell.Success("[v1, v2, v3]") == shell.run(
      "class Ver <: java.lang.Comparable[Ver] { public: val n: Int\n def this(n: Int) { this.n = n }\n def compareTo(o: Ver): Int = n - o.n\n def toString(): String = \"v\" + n }\ndef main(args: String[]): String { val xs = new java.util.ArrayList[Ver]()\n xs.add(new Ver(3))\n xs.add(new Ver(1))\n xs.add(new Ver(2))\n java.util.Collections::sort(xs)\n return \"\" + xs }", "None", Array()))
  }
  it("implements a user-defined self-referential generic interface") {
    assert(Shell.Success("ok") == shell.run(
      "interface Holder[T] { def value(): T }\nclass Node <: Holder[Node] { public: def this{}\n def value(): Node = self }\ndef main(args: String[]): String { return if new Node().value() != null { \"ok\" } else { \"no\" } }", "None", Array()))
  }
  it("still requires abstract methods of the implemented interface") {
    assert(Shell.Failure(-1) == shell.run(
      "class C <: java.util.List[String] { public: def this{} }\ndef main(args: String[]): void { }", "None", Array()))
  }
}
