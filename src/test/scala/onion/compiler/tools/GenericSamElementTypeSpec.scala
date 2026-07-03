package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #232: when a lambda with untyped parameters is passed to a
 * Java generic functional interface whose SAM parameter is a type variable
 * bound to a wildcard (e.g. Consumer<? super E>, Predicate<? super E>,
 * Comparator<? super E>), the receiver's concrete element type must propagate
 * to the lambda parameter. Previously the parameter was inferred as Object
 * (the wildcard's upper bound), so element method calls failed with E0005.
 */
class GenericSamElementTypeSpec extends AbstractShellSpec {

  describe("generic SAM element type propagation") {
    it("propagates element type into a bare Consumer lambda (forEach)") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("hi")
          |    val sb = new StringBuffer()
          |    list.forEach((x) -> sb.append(x.toUpperCase()))
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "SamForEach.on",
        Array()
      )
      assert(Shell.Success("HI") == result)
    }

    it("propagates element type into a bare Predicate lambda (removeIf)") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("bb")
          |    list.add("c")
          |    list.add("ddd")
          |    list.removeIf((x) -> x.length() > 2)
          |    return list.toString()
          |  }
          |}
          |""".stripMargin,
        "SamRemoveIf.on",
        Array()
      )
      assert(Shell.Success("[bb, c]") == result)
    }

    it("propagates element type into a bare Comparator lambda (List.sort)") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("bb")
          |    list.add("aaa")
          |    list.add("c")
          |    list.sort((a, b) -> a.compareTo(b))
          |    return list.toString()
          |  }
          |}
          |""".stripMargin,
        "SamListSort.on",
        Array()
      )
      assert(Shell.Success("[aaa, bb, c]") == result)
    }

    it("propagates element type into a bare Comparator lambda (Collections.sort)") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("bb")
          |    list.add("aaa")
          |    list.add("c")
          |    Collections::sort(list, (a, b) -> a.compareTo(b))
          |    return list.toString()
          |  }
          |}
          |""".stripMargin,
        "SamCollectionsSort.on",
        Array()
      )
      assert(Shell.Success("[aaa, bb, c]") == result)
    }

    it("still honors an explicit lambda parameter type") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("hi")
          |    val sb = new StringBuffer()
          |    list.forEach((x: String) -> sb.append(x.toUpperCase()))
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "SamExplicit.on",
        Array()
      )
      assert(Shell.Success("HI") == result)
    }

    it("still infers element type for the builtin extension pipeline") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = new ArrayList[String]()
          |    list.add("hi")
          |    return list.map { s => s.toUpperCase() }.toString()
          |  }
          |}
          |""".stripMargin,
        "SamBuiltinMap.on",
        Array()
      )
      assert(Shell.Success("[HI]") == result)
    }
  }
}
