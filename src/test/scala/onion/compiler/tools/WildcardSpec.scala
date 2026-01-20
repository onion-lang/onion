package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for wildcard type support in generics.
 * Tests cover:
 * - Wildcard syntax parsing (?, ? extends T, ? super T)
 * - Type compatibility with wildcards
 * - Passing wildcard-typed values to methods
 */
class WildcardSpec extends AbstractShellSpec {

  describe("Wildcard types") {

    describe("basic wildcard syntax") {
      it("accepts unbounded wildcard in type declaration") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list: List[?] = new ArrayList[String]()
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("accepts upper bounded wildcard") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list: List[? extends Number] = new ArrayList[Integer]()
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("accepts lower bounded wildcard") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list: List[? super Integer] = new ArrayList[Number]()
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }
    }

    describe("wildcard type compatibility - covariance (? extends)") {
      it("List[Integer] is assignable to List[? extends Number]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val intList: List[Integer] = new ArrayList[Integer]()
            |    val numList: List[? extends Number] = intList
            |    return numList.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("List[? extends Integer] is assignable to List[? extends Number]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val intList: List[? extends Integer] = new ArrayList[Integer]()
            |    val numList: List[? extends Number] = intList
            |    return numList.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("List[? extends T] is assignable to List[?]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val intList: List[? extends Integer] = new ArrayList[Integer]()
            |    val anyList: List[?] = intList
            |    return anyList.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }
    }

    describe("wildcard type compatibility - contravariance (? super)") {
      it("List[Number] is assignable to List[? super Integer]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val numList: List[Number] = new ArrayList[Number]()
            |    val superList: List[? super Integer] = numList
            |    return superList.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("List[? super Number] is assignable to List[? super Integer]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val superNum: List[? super Number] = new ArrayList[Object]()
            |    val superInt: List[? super Integer] = superNum
            |    return superInt.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }
    }

    describe("passing wildcard-typed values to methods") {
      it("passes List[Integer] to method expecting List[? extends Number]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def sumList(list: List[? extends Number]): Int {
            |    var sum = 0
            |    val iter = list.iterator()
            |    while(iter.hasNext()) {
            |      val n = iter.next()
            |      sum = sum + n.intValue()
            |    }
            |    return sum
            |  }
            |  static def main(args: String[]): Int {
            |    val list = new ArrayList[Integer]()
            |    list.add(1)
            |    list.add(2)
            |    list.add(3)
            |    return sumList(list)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(6))
      }

      it("passes List[? extends Integer] to method expecting List[? extends Number]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def processNumbers(list: List[? extends Number]): Int {
            |    return list.size()
            |  }
            |  static def main(args: String[]): Int {
            |    val list: List[? extends Integer] = new ArrayList[Integer]()
            |    return processNumbers(list)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("passes List[Number] to method expecting List[? super Integer]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def addIntegers(list: List[? super Integer]): Unit {
            |    list.add(1)
            |    list.add(2)
            |  }
            |  static def main(args: String[]): Int {
            |    val list = new ArrayList[Number]()
            |    addIntegers(list)
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2))
      }
    }

    describe("wildcard with Java interop") {
      it("calls Java method returning wildcard type") {
        val result = shell.run(
          """
            |import { java.util.Collections; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = new ArrayList[Integer]()
            |    list.add(3)
            |    list.add(1)
            |    list.add(2)
            |    Collections::sort(list)
            |    return list.get(0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1))
      }
    }

    describe("nested wildcards") {
      it("handles List[List[? extends Number]]") {
        val result = shell.run(
          """
            |import { java.util.List; java.util.ArrayList; }
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val innerList = new ArrayList[Integer]()
            |    innerList.add(42)
            |    val outerList = new ArrayList[List[? extends Number]]()
            |    outerList.add(innerList)
            |    return outerList.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1))
      }
    }
  }
}
