package onion.compiler.tools

import onion.tools.Shell

/** A record accessor must be return-compatible with the inherited method it overrides (#2372). */
class RecordAccessorOverrideSpec extends AbstractShellSpec {

  describe("Record accessor vs inherited method") {

    it("rejects a component whose accessor returns a different type than the interface method") {
      val result = shell.run(
        """
          |interface Shape { def area(): Double }
          |record Circle(area: Int) conforms Shape
          |IO::println(new Circle(3).area())
          |""".stripMargin,
        "RecordAccessorClash.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects the same clash in an ADT enum case") {
      val result = shell.run(
        """
          |enum Shape {
          |  case Circle(area: Int)
          |public:
          |  def area(): Double = 0.0
          |}
          |val s: Shape = new Circle(3)
          |IO::println(s.area())
          |""".stripMargin,
        "EnumCaseAccessorClash.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts a component whose accessor matches the interface method") {
      val result = shell.run(
        """
          |interface Shape { def area(): Int }
          |record Circle(area: Int) conforms Shape
          |IO::println(new Circle(3).area())
          |""".stripMargin,
        "RecordAccessorOk.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }
}
