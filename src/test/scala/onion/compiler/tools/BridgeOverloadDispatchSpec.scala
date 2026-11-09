package onion.compiler.tools

import onion.tools.Shell

// #2559: when a class overloads a generic supertype's method with both the
// substituted type argument (`n(x: JInteger)` for `T = JInteger`) and its
// primitive (`n(x: Int)`), the bridge must call the exact override, as Java
// does. The primitive overload is bridged to only when no exact one exists.
class BridgeOverloadDispatchSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(body.stripMargin, "BridgeOverloadDispatch.on", Array())

  describe("A bridge to an overloaded generic override (#2559)") {
    it("calls n(JInteger), not n(Int), through G[JInteger]") {
      val result = run(
        """
          |interface G[T] { def n(x: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int): JInteger = 1
          |  def n(x: JInteger): JInteger = 2
          |  static def main(args: String[]): JInteger {
          |    val g: G[JInteger] = new C
          |    return g.n(4)
          |  }
          |}
          |""")
      assert(Shell.Success(2) == result)
    }

    it("calls n(JInteger) whichever overload is declared first") {
      val result = run(
        """
          |interface G[T] { def n(x: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: JInteger): JInteger = 2
          |  def n(x: Int): JInteger = 1
          |  static def main(args: String[]): JInteger {
          |    val g: G[JInteger] = new C
          |    return g.n(4)
          |  }
          |}
          |""")
      assert(Shell.Success(2) == result)
    }

    it("calls the exact override through a generic abstract superclass") {
      val result = run(
        """
          |abstract class B[T] {
          |public:
          |  def this {}
          |  abstract def n(x: T): JInteger
          |}
          |class C extends B[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int): JInteger = 1
          |  def n(x: JInteger): JInteger = 2
          |  static def main(args: String[]): JInteger {
          |    val b: B[JInteger] = new C
          |    return b.n(4)
          |  }
          |}
          |""")
      assert(Shell.Success(2) == result)
    }

    it("prefers the overload with more exact parameters") {
      val result = run(
        """
          |interface G[T] { def n(x: T, y: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int, y: JInteger): JInteger = 1
          |  def n(x: JInteger, y: JInteger): JInteger = 2
          |  static def main(args: String[]): JInteger {
          |    val g: G[JInteger] = new C
          |    return g.n(4, 5)
          |  }
          |}
          |""")
      assert(Shell.Success(2) == result)
    }

    it("still bridges to n(Int) when it is the only candidate") {
      val result = run(
        """
          |interface G[T] { def n(x: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int): JInteger = x + 1
          |  static def main(args: String[]): JInteger {
          |    val g: G[JInteger] = new C
          |    return g.n(4)
          |  }
          |}
          |""")
      assert(Shell.Success(5) == result)
    }

    it("leaves a direct call with an Int argument on n(Int)") {
      val result = run(
        """
          |interface G[T] { def n(x: T): JInteger }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int): JInteger = 1
          |  def n(x: JInteger): JInteger = 2
          |  static def main(args: String[]): JInteger {
          |    val c = new C
          |    return c.n(4)
          |  }
          |}
          |""")
      assert(Shell.Success(1) == result)
    }
  }
}
