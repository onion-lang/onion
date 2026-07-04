package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #231: an `as` cast between two parameterizations of the same
 * (or an erasure-compatible) raw generic type. Because Onion generics are
 * erasure-based, `List[String] as List[Object]` is a valid unchecked cast — at
 * runtime both are the raw `java.util.List`, so the checkcast targets the erased
 * class. Invariant type arguments used to make the typer reject it (E0000); the
 * fix compares the raw erasures. Casts between unrelated erasures (List vs Map,
 * String vs Int) must still be rejected.
 */
class GenericCastSpec extends AbstractShellSpec {

  describe("generic cast between differing type arguments (#231)") {
    it("widens List[String] to List[Object]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[String] = ["a", "b"]
          |    val ys: List[Object] = (xs as List[Object])
          |    return (ys[0] as String) + ys.size
          |  }
          |}
          |""".stripMargin,
        "GenericCastWiden.on",
        Array()
      )
      assert(Shell.Success("a2") == result)
    }

    it("casts Map[String, Int] to Map[Object, Object]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m: Map[String, Int] = ["a": 1, "b": 2]
          |    val o: Map[Object, Object] = (m as Map[Object, Object])
          |    return "" + o.size
          |  }
          |}
          |""".stripMargin,
        "GenericCastMap.on",
        Array()
      )
      assert(Shell.Success("2") == result)
    }

    it("narrows List[Object] back to List[String]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o: List[Object] = ["a", "b"]
          |    val s: List[String] = (o as List[String])
          |    return s[0]
          |  }
          |}
          |""".stripMargin,
        "GenericCastNarrow.on",
        Array()
      )
      assert(Shell.Success("a") == result)
    }

    it("allows an interface-to-interface cast (List -> Map) at compile time (#282)") {
      // Per JLS 5.5.1 (and matching javac), a cast between two non-final
      // interfaces is legal at compile time — a single class could implement
      // both List and Map — so the check is deferred to the runtime checkcast.
      // It therefore compiles cleanly (and would throw a ClassCastException at
      // runtime for a value that is not actually a Map).
      val compiles = shell.compiles(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[String] = ["a"]
          |    val m: Map[Object, Object] = (xs as Map[Object, Object])
          |    return "" + m.size
          |  }
          |}
          |""".stripMargin,
        "GenericCastUnrelated.on"
      )
      assert(compiles)
    }

    it("still rejects an unrelated primitive cast (String -> Int)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = "x"
          |    val n: Int = (s as Int)
          |    return "" + n
          |  }
          |}
          |""".stripMargin,
        "GenericCastStringInt.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
