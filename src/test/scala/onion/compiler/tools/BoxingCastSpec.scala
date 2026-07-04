package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #261: an explicit `as` cast of a primitive to a boxing
 * supertype (Object / Number / Comparable / Serializable / exact wrapper)
 * must autobox rather than being rejected with E0000. The equivalent
 * implicit assignment already boxes; the `as` path now matches it.
 */
class BoxingCastSpec extends AbstractShellSpec {

  describe("boxing casts") {
    it("casts Int to Object via `as`") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 5
          |    val o: Object = (x as Object)
          |    return o.toString()
          |  }
          |}
          |""".stripMargin,
        "BoxingCastObject.on",
        Array()
      )
      assert(Shell.Success("5") == result)
    }

    it("casts Int to Number via `as` and unboxes back") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 7
          |    val n: Number = (x as Number)
          |    return n.intValue().toString()
          |  }
          |}
          |""".stripMargin,
        "BoxingCastNumber.on",
        Array()
      )
      assert(Shell.Success("7") == result)
    }

    it("casts Double to Number via `as`") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d: Double = 3.5
          |    val n: Number = (d as Number)
          |    return n.doubleValue().toString()
          |  }
          |}
          |""".stripMargin,
        "BoxingCastDouble.on",
        Array()
      )
      assert(Shell.Success("3.5") == result)
    }

    it("casts Boolean to Object via `as`") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Boolean = true
          |    val o: Object = (b as Object)
          |    return o.toString()
          |  }
          |}
          |""".stripMargin,
        "BoxingCastBoolean.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("still casts Int to its exact wrapper via `as`") {
      val result = shell.run(
        """
          |import { java.lang.Integer as JInteger }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 5
          |    val o: JInteger = (x as JInteger)
          |    return o.toString()
          |  }
          |}
          |""".stripMargin,
        "BoxingCastWrapper.on",
        Array()
      )
      assert(Shell.Success("5") == result)
    }

    it("casts Int to Serializable via `as`") {
      val result = shell.run(
        """
          |import { java.io.Serializable }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 42
          |    val s: Serializable = (x as Serializable)
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "BoxingCastSerializable.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("rejects a cast to an unrelated reference type (Int as String)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 5
          |    val s: String = (x as String)
          |    return s
          |  }
          |}
          |""".stripMargin,
        "BoxingCastReject.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects Char as Number (Character is not a Number)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ch: Char = 'a'
          |    val n: Number = (ch as Number)
          |    return "bad"
          |  }
          |}
          |""".stripMargin,
        "BoxingCastCharNumber.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
