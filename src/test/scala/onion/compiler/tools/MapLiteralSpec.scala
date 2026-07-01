package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for map literals: ["key": value, ...] and the empty map [:].
 * Map literals build a java.util.LinkedHashMap (insertion order preserved)
 * typed as Map[K, V].
 */
class MapLiteralSpec extends AbstractShellSpec {

  describe("Map literals") {
    it("creates a map and reads values back") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val ages = ["a": 1, "b": 2, "c": 3]
          |    return (ages.get("a") as Integer) + (ages.get("c") as Integer) + ages.size()
          |  }
          |}
          |""".stripMargin,
        "MapLiteralBasic.on",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("creates an empty map with [:]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val empty = [:]
          |    return empty.size()
          |  }
          |}
          |""".stripMargin,
        "EmptyMapLiteral.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("preserves insertion order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["one": 1, "two": 2, "three": 3]
          |    var keys = ""
          |    foreach k: String in m.keySet() {
          |      keys = keys + k + ","
          |    }
          |    return keys
          |  }
          |}
          |""".stripMargin,
        "MapLiteralOrder.on",
        Array()
      )
      assert(Shell.Success("one,two,three,") == result)
    }

    it("supports nested collection literals") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = ["xs": [1, 2, 3], "ys": [4, 5]]
          |    val xs = m.get("xs") as List
          |    val ys = m.get("ys") as List
          |    return xs.size() + ys.size()
          |  }
          |}
          |""".stripMargin,
        "NestedMapLiteral.on",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("can be declared with an explicit Map type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m: Map[String, Int] = ["k": 42]
          |    return m.get("k") as Integer
          |  }
          |}
          |""".stripMargin,
        "TypedMapLiteral.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("boxes primitive keys and values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val squares = [1: 1, 2: 4, 3: 9]
          |    return squares.get(3) as Integer
          |  }
          |}
          |""".stripMargin,
        "PrimitiveMapLiteral.on",
        Array()
      )
      assert(Shell.Success(9) == result)
    }

    it("does not break list literals") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    val empty = []
          |    return xs.size() + empty.size()
          |  }
          |}
          |""".stripMargin,
        "ListLiteralStillWorks.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
  }
}
