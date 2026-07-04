package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic constructor checks its arguments under the type-argument
 * substitution (issue #263): `new Box[String](aStringBuilder)` is a type error,
 * not a compile-through that ClassCastExceptions at runtime. Valid calls
 * (matching, supertype, boxing, nested generics) still work.
 */
class GenericConstructorArgTypeSpec extends AbstractShellSpec {
  private val box = "class Box[T] { val value: T\npublic: def this(v: T) { this.value = v }\n def get(): T = value }\n"
  it("rejects an argument incompatible with the type argument") {
    assert(Shell.Failure(-1) == shell.run(
      box + "def main(args: String[]): void { val sb = new java.lang.StringBuilder()\n val b: Box[String] = new Box[String](sb) }", "None", Array()))
  }
  it("accepts a matching argument") {
    assert(Shell.Success("HI") == shell.run(
      box + "def main(args: String[]): String { return new Box[String](\"hi\").get().toUpperCase() }", "None", Array()))
  }
  it("accepts a supertype type argument") {
    assert(Shell.Success(2) == shell.run(
      box + "def main(args: String[]): Int { val b = new Box[java.lang.CharSequence](new java.lang.StringBuilder(\"hi\"))\n return b.get().length() }", "None", Array()))
  }
  it("boxes a primitive argument for a wrapper type argument") {
    assert(Shell.Success(100) == shell.run(
      box + "def main(args: String[]): Int { return new Box[Integer](99).get() + 1 }", "None", Array()))
  }
  it("accepts a nested generic argument") {
    assert(Shell.Success(2) == shell.run(
      box + "def main(args: String[]): Int { return new Box[List[String]]([\"a\", \"b\"]).get().size() }", "None", Array()))
  }
}
