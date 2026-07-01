package onion.compiler.tools

import onion.tools.Shell

class MapsSpec extends AbstractShellSpec {
  describe("Maps library") {

    it("provides getOrDefault") {
      val result = shell.run(
        """
          |import { onion.Maps }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = Maps::newMap[String, Int]()
          |    m.put("a", 1)
          |    val present = Maps::getOrDefault(m, "a", 99)
          |    val missing = Maps::getOrDefault(m, "b", 99)
          |    return present + missing
          |  }
          |}
          |""".stripMargin,
        "MapsGetOrDefault.on",
        Array()
      )
      assert(Shell.Success(100) == result)
    }

    it("filters entries by value") {
      val result = shell.run(
        """
          |import { onion.Maps }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = Maps::newMap[String, Int]()
          |    m.put("a", 10)
          |    m.put("b", 25)
          |    m.put("c", 5)
          |    val filtered = Maps::filterValues(m, (v: Int) -> v >= 10)
          |    return filtered.size()
          |  }
          |}
          |""".stripMargin,
        "MapsFilterValues.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("maps values") {
      val result = shell.run(
        """
          |import { onion.Maps }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = Maps::newMap[String, Int]()
          |    m.put("a", 1)
          |    m.put("b", 2)
          |    val doubled = Maps::mapValues(m, (v: Int) -> v * 2)
          |    return doubled.get("a") as Int + doubled.get("b") as Int
          |  }
          |}
          |""".stripMargin,
        "MapsMapValues.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("merges two maps") {
      val result = shell.run(
        """
          |import { onion.Maps }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a = Maps::newMap[String, Int]()
          |    a.put("x", 1)
          |    val b = Maps::newMap[String, Int]()
          |    b.put("x", 10)
          |    b.put("y", 20)
          |    val merged = Maps::merge(a, b)
          |    return merged.get("x") as Int + merged.get("y") as Int
          |  }
          |}
          |""".stripMargin,
        "MapsMerge.on",
        Array()
      )
      assert(Shell.Success(30) == result)
    }
  }
}
