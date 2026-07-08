package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #319: `map` on a RAW List (erasure context, unbound element
 * type) must work the same way `filter` already does. Previously `map`
 * self-contradicted with "type Function1[T, Int] is expected, but type
 * Function1[T, Int] is used" because the builtin extension's SAM return
 * position (`R`, inferred to boxed Integer) was compared against the closure's
 * primitive `int` result with a non-boxing-aware terminal comparison.
 */
class RawListMapSpec extends AbstractShellSpec {

  describe("raw List map (#319)") {
    it("maps over a raw List, treating the element as Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val raw = [1, 2, 3] as List
          |    return raw.map { e => (e as Int) + 1 }.toString()
          |  }
          |}
          |""".stripMargin,
        "RawListMap.on",
        Array()
      )
      assert(Shell.Success("[2, 3, 4]") == result)
    }

    it("still maps over a typed List[Int]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return ([1, 2, 3] as List[Int]).map { e => (e as Int) + 1 }.toString()
          |  }
          |}
          |""".stripMargin,
        "TypedListMap.on",
        Array()
      )
      assert(Shell.Success("[2, 3, 4]") == result)
    }

    it("maps over an everyday declared List[Int]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[Int] = [1, 2, 3]
          |    return xs.map { x => x + 1 }.toString()
          |  }
          |}
          |""".stripMargin,
        "EverydayListMap.on",
        Array()
      )
      assert(Shell.Success("[2, 3, 4]") == result)
    }

    it("still filters over a raw List") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val raw = [1, 2, 3] as List
          |    return raw.filter { e => (e as Int) > 1 }.toString()
          |  }
          |}
          |""".stripMargin,
        "RawListFilter.on",
        Array()
      )
      assert(Shell.Success("[2, 3]") == result)
    }
  }
}
