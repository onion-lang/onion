package onion.compiler.tools

import onion.tools.Shell

/**
 * A nullable applied-generic value whose type argument is a type variable
 * (`Node[T]?`) must match a parameter of the same type — the common
 * generic-container-with-nullable-links pattern (issue #295). Invariant
 * generics stay enforced.
 */
class NullableGenericArgSpec extends AbstractShellSpec {
  it("matches a Node[T]? argument to a Node[T]? parameter") {
    assert(Shell.Success(0) == shell.run(
      "class Node[T] { public: val v: T\n def this(x: T) { this.v = x } }\nclass C[T] { var n: Node[T]?\npublic: def this { this.n = null }\n def f(node: Node[T]?): Int = 0\n def g(): Int = f(n) }\ndef main(args: String[]): Int = new C[Integer]().g()",
      "None", Array()))
  }
  it("widens a non-null Node[T] argument to a Node[T]? parameter") {
    assert(Shell.Success(0) == shell.run(
      "class Node[T] { public: val v: T\n def this(x: T) { this.v = x } }\nclass C[T] { public: def this {}\n def f(node: Node[T]?): Int = 0\n def g(x: T): Int = f(new Node[T](x)) }\ndef main(args: String[]): Int = new C[Integer]().g(1)",
      "None", Array()))
  }
  it("still enforces invariant generics (List[String] is not List[Integer])") {
    assert(Shell.Failure(-1) == shell.run(
      "def take(xs: List[Integer]): Int = xs.size()\ndef main(args: String[]): void { val ss: List[String] = [\"a\"]\n take(ss) }",
      "None", Array()))
  }
}
