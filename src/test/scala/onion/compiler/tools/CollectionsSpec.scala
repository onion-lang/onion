package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the Collections standard library module.
 * Note: Tests using lambda/closure functions are commented out due to
 * compatibility issues with static method generic inference.
 */
class CollectionsSpec extends AbstractShellSpec {

  describe("Collections module") {

    describe("list creation") {
      it("creates a list with listOf3") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf3(1, 2, 3)
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }

      it("creates range") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::range(1, 5)
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(4))
      }

      it("accesses range elements") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::range(10, 15)
            |    return list.get(2)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(12))
      }
    }

    describe("set creation") {
      it("creates a set with setOf3") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val set = Colls::setOf3("a", "b", "c")
            |    return set.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }

      it("set eliminates duplicates") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val set = Colls::mutableSetOf3("a", "a", "b")
            |    return set.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2))
      }
    }

    describe("map creation") {
      it("creates a map with mapOf") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val e1 = Colls::entry("name", "Alice")
            |    val e2 = Colls::entry("city", "Tokyo")
            |    val map = Colls::mapOf2(e1, e2)
            |    return map.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2))
      }

      it("gets value from map") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val e1 = Colls::entry("name", "Alice")
            |    val map = Colls::mapOf1(e1)
            |    return map.get("name")
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("Alice"))
      }
    }

    describe("take and drop") {
      it("takes first n elements") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf5(1, 2, 3, 4, 5)
            |    val taken = Colls::take(list, 3)
            |    return taken.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }

      it("drops first n elements") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf5(1, 2, 3, 4, 5)
            |    val dropped = Colls::drop(list, 2)
            |    return dropped.get(0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }
    }

    describe("reverse and sorted") {
      it("reverses a list") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf3(1, 2, 3)
            |    val reversed = Colls::reverse(list)
            |    return reversed.get(0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }

      it("sorts a list") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf3(3, 1, 2)
            |    val sorted = Colls::sorted(list)
            |    return sorted.get(0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1))
      }
    }

    describe("distinct") {
      it("removes duplicates") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf6(1, 2, 2, 3, 3, 3)
            |    val unique = Colls::distinct(list)
            |    return unique.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }
    }

    describe("concat") {
      it("concatenates two lists") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list1 = Colls::listOf2(1, 2)
            |    val list2 = Colls::listOf2(3, 4)
            |    val combined = Colls::concat(list1, list2)
            |    return combined.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(4))
      }
    }

    describe("first and last") {
      it("gets first element") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf3(1, 2, 3)
            |    return Colls::first(list)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1))
      }

      it("gets last element") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf3(1, 2, 3)
            |    return Colls::last(list)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }
    }

    describe("size and empty checks") {
      it("checks size") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf3(1, 2, 3)
            |    return Colls::size(list)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }

      it("checks isEmpty for non-empty list") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val list = Colls::listOf1(1)
            |    return Colls::isEmpty(list)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(false))
      }
    }

    describe("map operations") {
      it("gets keys from map") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val e1 = Colls::entry("a", 1)
            |    val e2 = Colls::entry("b", 2)
            |    val map = Colls::mapOf2(e1, e2)
            |    val keys = Colls::keys(map)
            |    return keys.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2))
      }

      it("gets values from map") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val e1 = Colls::entry("a", 1)
            |    val e2 = Colls::entry("b", 2)
            |    val map = Colls::mapOf2(e1, e2)
            |    val vals = Colls::values(map)
            |    return vals.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2))
      }
    }

    describe("set operations") {
      it("computes union") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val set1 = Colls::setOf3(1, 2, 3)
            |    val set2 = Colls::setOf3(3, 4, 5)
            |    val u = Colls::union(set1, set2)
            |    return u.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(5))
      }

      it("computes intersection") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val set1 = Colls::setOf3(1, 2, 3)
            |    val set2 = Colls::setOf3(2, 3, 4)
            |    val i = Colls::intersection(set1, set2)
            |    return i.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2))
      }

      it("computes difference") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val set1 = Colls::setOf3(1, 2, 3)
            |    val set2 = Colls::setOf2(2, 3)
            |    val d = Colls::difference(set1, set2)
            |    return d.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1))
      }
    }

    describe("conversion") {
      it("converts list to set") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val list = Colls::listOf6(1, 2, 2, 3, 3, 3)
            |    val set = Colls::toSet(list)
            |    return set.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }

      it("converts set to list") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    val set = Colls::setOf3(1, 2, 3)
            |    val list = Colls::toList(set)
            |    return list.size()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3))
      }
    }
  }
}
