package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic method whose parameter contains a nullable type parameter (`List[T?]`)
 * now infers T from the argument (issue #254). Previously `unify` had no case for a
 * NullableType formal, so `T?` bound nothing and T defaulted to Object, rejecting an
 * otherwise-matching `List[String?]` argument.
 */
class NullableTypeParamInferenceSpec extends AbstractShellSpec {
  it("infers T from a List[T?] argument and finds the first non-null") {
    assert(Shell.Success("hit") == shell.run(
      "def firstNonNull[T](xs: List[T?]): T? { foreach x: T? in xs { if x != null { return x } }\n return null }\ndef main(args: String[]): String { val a: List[String?] = [null, \"hit\", null]\n return firstNonNull(a) ?: \"MISS\" }", "None", Array()))
  }
  it("infers T from a List[T?] argument returning null") {
    assert(Shell.Success("none") == shell.run(
      "def firstNonNull[T](xs: List[T?]): T? { return xs.get(0) }\ndef main(args: String[]): String { val a: List[String?] = [null, \"hit\"]\n return firstNonNull(a) ?: \"none\" }", "None", Array()))
  }
  it("still infers T from a non-nullable List[T]") {
    assert(Shell.Success("a") == shell.run(
      "def firstOf[T](xs: List[T]): T = xs.get(0)\ndef main(args: String[]): String = firstOf([\"a\", \"b\"])", "None", Array()))
  }
}
