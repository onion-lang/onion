package onion.compiler.tools

import onion.tools.Shell

/**
 * Extending a generic parent with a PRIMITIVE type argument must resolve the
 * super constructor (issue #272). The parent's `def this(v: T)` is erased to
 * `Box(Object)`, so a primitive super-init argument (`Box[Int](v)`) does not
 * match by exact signature; the super path now applies the same substituted +
 * boxing constructor match that `new Box[Int](42)` already uses. Reference type
 * arguments continue to work, and a genuinely incompatible argument is rejected.
 */
class GenericSuperConstructorPrimitiveSpec extends AbstractShellSpec {
  private val box =
    "class Box[T] { val value: T\n" +
    "public: def this(v: T) { value = v }\n" +
    " def get(): T { return value } }\n"

  it("resolves the super constructor for a primitive type argument (Int)") {
    assert(Shell.Success(42) == shell.run(
      box +
      "class IntBox(v: Int) : Box[Int](v) {\n" +
      "public: def unwrap(): Int { return (get() as Int) } }\n" +
      "class Main { public: static def main(args: String[]): Int { return new IntBox(42).unwrap() } }",
      "None", Array()))
  }

  it("resolves the super constructor for a primitive Double type argument") {
    assert(Shell.Success(3) == shell.run(
      box +
      "class DBox(v: Double) : Box[Double](v) {\n" +
      "public: def unwrap(): Int { return ((get() as Double) as Int) } }\n" +
      "class Main { public: static def main(args: String[]): Int { return new DBox(3.5).unwrap() } }",
      "None", Array()))
  }

  it("still resolves the super constructor for a reference type argument") {
    assert(Shell.Success("HI") == shell.run(
      box +
      "class SBox(v: String) : Box[String](v) {\n" +
      "public: def unwrap(): String { return (get() as String) } }\n" +
      "class Main { public: static def main(args: String[]): String { return new SBox(\"hi\").unwrap().toUpperCase() } }",
      "None", Array()))
  }

  it("resolves the super constructor for a multi-parameter mixed primitive/reference parent") {
    assert(Shell.Success(5) == shell.run(
      "class Pair[A, B] { val a: A\n val b: B\n" +
      "public: def this(x: A, y: B) { a = x\n b = y }\n" +
      " def first(): A { return a } }\n" +
      "class IntStr(x: Int, y: String) : Pair[Int, String](x, y) {\n" +
      "public: def fst(): Int { return (first() as Int) } }\n" +
      "class Main { public: static def main(args: String[]): Int { return new IntStr(5, \"z\").fst() } }",
      "None", Array()))
  }

  it("rejects a super-init argument incompatible with the primitive type argument") {
    assert(Shell.Failure(-1) == shell.run(
      box +
      "class BadBox(v: Boolean) : Box[Int](v) {\n" +
      "public: def u(): Int { return (get() as Int) } }\n" +
      "class Main { public: static def main(args: String[]): void { } }",
      "None", Array()))
  }
}
