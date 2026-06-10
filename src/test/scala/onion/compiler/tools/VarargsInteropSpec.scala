package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for calling Java varargs APIs and static interface methods.
 * Covers: vararg flag detection on loaded methods, argument packing,
 * fixed-arity-over-varargs overload phasing, InterfaceMethodref emission
 * for static interface methods, and the array-cache fix that kept type
 * variables from leaking across method signatures.
 */
class VarargsInteropSpec extends AbstractShellSpec {

  describe("Java varargs interop") {
    it("calls String.format with varargs") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return String::format("x=%d, y=%s", 42, "hello")
          |  }
          |}
          |""".stripMargin,
        "StringFormat.on",
        Array()
      )
      assert(Shell.Success("x=42, y=hello") == result)
    }

    it("calls Arrays.asList with spread arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Arrays::asList("a", "b", "c")
          |    return xs.get(0) + xs.get(2) + xs.size()
          |  }
          |}
          |""".stripMargin,
        "ArraysAsList.on",
        Array()
      )
      assert(Shell.Success("ac3") == result)
    }

    it("prefers the fixed-arity overload of List.of") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = List::of(1, 2, 3)
          |    return xs.size()
          |  }
          |}
          |""".stripMargin,
        "ListOfFixedArity.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("falls back to the vararg overload of List.of beyond ten arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = List::of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
          |    return xs.size()
          |  }
          |}
          |""".stripMargin,
        "ListOfVararg.on",
        Array()
      )
      assert(Shell.Success(12) == result)
    }

    it("calls static interface methods (Path.of) without IncompatibleClassChangeError") {
      val result = shell.run(
        """
          |import { java.nio.file.Path }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = Path::of("tmp", "foo", "bar.txt")
          |    return p.getFileName().toString()
          |  }
          |}
          |""".stripMargin,
        "PathOf.on",
        Array()
      )
      assert(Shell.Success("bar.txt") == result)
    }

    it("calls a vararg method with zero variable arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return String::format("plain")
          |  }
          |}
          |""".stripMargin,
        "VarargZeroArgs.on",
        Array()
      )
      assert(Shell.Success("plain") == result)
    }
  }
}
