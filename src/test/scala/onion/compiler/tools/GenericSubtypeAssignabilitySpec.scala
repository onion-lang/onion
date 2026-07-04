package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic subtype with a type-variable argument is assignable to its generic
 * supertype (issue #269): `ArrayList[T]` to `List[T]`, `BoxC[T]` to `Container[T]`.
 * The assignability check used for a type-variable-parameterized expected type
 * (structurallyAssignable) previously required the same raw class, missing the
 * class hierarchy; it now consults the actual's applied supertype view. Invariant
 * generics (List[String] is not List[Integer]) are still enforced.
 */
class GenericSubtypeAssignabilitySpec extends AbstractShellSpec {
  it("returns an ArrayList[T] as List[T] from a generic method") {
    assert(Shell.Success(2) == shell.run(
      "def make[T](x: T): List[T] { val r = new java.util.ArrayList[T]()\n r.add(x)\n return r }\ndef main(args: String[]): Int = make(\"hi\").get(0).length()", "None", Array()))
  }
  it("returns an ArrayList[T] as Collection[T]") {
    assert(Shell.Success(1) == shell.run(
      "def wrap[T](x: T): java.util.Collection[T] { val r = new java.util.ArrayList[T]()\n r.add(x)\n return r }\ndef main(args: String[]): Int = wrap(\"hi\").size()", "None", Array()))
  }
  it("assigns a user generic subtype to its generic interface with a type variable") {
    assert(Shell.Success(2) == shell.run(
      "interface Container[T] { def item(): T }\nclass BoxC[T] <: Container[T] { val v: T\npublic: def this(x: T) { this.v = x }\n def item(): T = v }\ndef wrap[T](x: T): Container[T] = new BoxC[T](x)\ndef main(args: String[]): Int = wrap(\"hi\").item().length()", "None", Array()))
  }
  it("still enforces invariant generics (List[String] is not List[Integer])") {
    assert(Shell.Failure(-1) == shell.run(
      "def take(xs: List[Integer]): Int = xs.size()\ndef main(args: String[]): void { val ss: List[String] = [\"a\"]\n take(ss) }", "None", Array()))
  }
}
