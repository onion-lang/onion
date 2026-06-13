package onion.compiler.tools

import onion.tools.Shell

/**
 * `new Type[expr]` with a non-literal size expression must allocate an array of
 * that size, not be misread as the parameterized type `Type[expr]`. The grammar
 * disambiguates by trailing context: a bracketed TYPE list followed by `(` is a
 * type-argument list (generic instantiation); a bracketed EXPRESSION is an array
 * dimension. This covers dynamic sizes, multi-dimensional arrays, size
 * expressions, and that generic `new C[T](...)` still parses.
 */
class DynamicArraySizeSpec extends AbstractShellSpec {
  describe("new Type[expr] array allocation") {
    it("allocates a String array using a variable size") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Int = 3
          |    val arr = new String[n]
          |    arr[0] = "a"
          |    arr[1] = "b"
          |    arr[2] = "c"
          |    return arr[0] + arr[1] + arr[2] + ":" + arr.length
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("abc:3") == result)
    }

    it("still allocates with a literal size") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr = new Int[4]
          |    arr[0] = 10
          |    arr[3] = 32
          |    return arr[0] + arr[3] + arr.length
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(46) == result)
    }

    it("allocates a multi-dimensional array with variable sizes") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val rows: Int = 2
          |    val cols: Int = 3
          |    val grid = new Int[rows][cols]
          |    grid[0][0] = 1
          |    grid[1][2] = 9
          |    return grid[0][0] + grid[1][2]
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(10) == result)
    }

    it("uses an arbitrary expression as the size") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val base: Int = 2
          |    val arr = new Int[base * 3 + 1]
          |    return arr.length
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("uses a member access as the size (new Int[arr.length])") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val src = new Int[5]
          |    val copy = new Int[src.length]
          |    return copy.length
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("uses a method call as the size (new String[list.size()])") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list = new ArrayList[String]()
          |    list.add("a")
          |    list.add("b")
          |    list.add("c")
          |    val arr = new String[list.size()]
          |    return arr.length
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("keeps generic instantiation (new C[T]()) working") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list = new ArrayList[String]()
          |    list.add("x")
          |    list.add("y")
          |    val m = new HashMap[String, Integer]()
          |    m.put("k", 5)
          |    return list.size() + m.get("k")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(7) == result)
    }
  }
}
