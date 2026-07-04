package onion.compiler.tools

import onion.tools.Shell

/**
 * Assigning the null literal to a non-null generic reference type whose type
 * argument is the enclosing class type variable (e.g. `Node[T]`) must be a
 * W0012 nullToNonNullable warning (warn-and-allow), not a hard E0000 error —
 * consistent with `null -> Node`, `null -> Node[Int]`, and `null -> T`, which
 * all compile (issue #283). The nullable form `Node[T]?` still fully suppresses
 * the warning, and a genuine type mismatch (wrong non-null value) still errors.
 */
class NullToTypeVariableGenericSpec extends AbstractShellSpec {
  it("compiles null assigned to a Node[T] field (warn-and-allow, was E0000)") {
    assert(Shell.Success(0) == shell.run(
      "class Node[T] {\n var next: Node[T]\npublic:\n def this() { this.next = null }\n def firstNull(): Int { if this.next == null { return 0 } else { return 1 } }\n}\ndef main(args: String[]): Int = new Node[Int]().firstNull()",
      "None", Array()))
  }

  it("compiles null assigned to a local of type Node[T] (context-independent)") {
    assert(Shell.Success(0) == shell.run(
      "def make[T](): Int { val n: Node[T] = null\n if n == null { return 0 } else { return 1 } }\nclass Node[T] { var x: T }\ndef main(args: String[]): Int = make[Integer]()",
      "None", Array()))
  }

  it("still fully suppresses on the nullable form Node[T]?") {
    assert(Shell.Success(0) == shell.run(
      "class Node[T] {\n var next: Node[T]?\npublic:\n def this() { this.next = null }\n def firstNull(): Int { if this.next == null { return 0 } else { return 1 } }\n}\ndef main(args: String[]): Int = new Node[Int]().firstNull()",
      "None", Array()))
  }

  it("still errors on a genuine type mismatch into a Node[T] field") {
    assert(Shell.Failure(-1) == shell.run(
      "class Node[T] {\n var next: Node[T]\npublic:\n def this() { this.next = \"oops\" }\n}\ndef main(args: String[]): void { new Node[Int]() }",
      "None", Array()))
  }
}
