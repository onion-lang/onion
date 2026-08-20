package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as an argument to an `enum case`
 * constructor invocation (`new Circle(try {...} catch {...})`) is another
 * sibling of the #669/#745/#752 stack-corruption family: the JVM clears the
 * operand stack when it dispatches to an exception handler, so an
 * already-pushed receiver would be silently discarded on the exceptional
 * path if evaluation weren't routed through a spill-to-locals-and-reload
 * sequence.
 *
 * An `enum case`'s generated constructor already flows through the same
 * `visitNewObject` path an ordinary record's constructor uses (see
 * `TryInConstructorArgAndCollectionLiteralSpec`), which is already
 * hardened, so this already passes; this closes a coverage gap rather than
 * a live bug. (`TryAsCallArgumentSpec` covers a *homogeneous* enum's
 * `select` arms and a plain instance-method receiver, but not a
 * `case`-enum's own constructor call.)
 */
class TryInEnumCaseArgSpec extends AbstractShellSpec {
  describe("try/catch expression as an enum case constructor argument") {
    it("does not corrupt the case record across a caught constructor argument") {
      val result = shell.run(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |  case Square(side: Double)
          |public:
          |  def area(): Double = select this {
          |    case c is Circle: c.radius() * c.radius()
          |    case s is Square: s.side() * s.side()
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c1: Shape = new Circle(try { Double::parseDouble("nope") } catch e: Exception { 2.0 })
          |    val c2: Shape = new Circle(try { Double::parseDouble("3.0") } catch e: Exception { -1.0 })
          |    return "" + c1.area() + "|" + c2.area()
          |  }
          |}
          |""".stripMargin,
        "TryInEnumCaseArg.on",
        Array()
      )
      assert(Shell.Success("4.0|9.0") == result)
    }
  }
}
