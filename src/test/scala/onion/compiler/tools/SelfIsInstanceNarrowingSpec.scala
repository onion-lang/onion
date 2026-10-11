package onion.compiler.tools

import onion.tools.Shell

/**
 * `self`/`this` is bound as an ordinary local inside an `extension T { ... }`
 * method, so `self is SomeSubtype`/`this is SomeSubtype` should smart-cast it
 * like a type-pattern check on any other immutable local does -- the same way
 * a null check on `self`/`this` already narrows it (`NullableSelfNarrowingSpec`).
 * Before this fix, `if self is Circle { self.radius() }` inside an `extension
 * Shape { ... }` method failed with a METHOD_NOT_FOUND on `Shape.radius()`,
 * even though the identical check already narrows an ordinary local.
 */
class SelfIsInstanceNarrowingSpec extends AbstractShellSpec {

  describe("is-instance narrowing of self/this in an extension method") {

    it("narrows self in the then-branch of if self is Subtype") {
      val result = shell.run(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |  case Square(side: Double)
          |}
          |extension Shape {
          |  def area(): Double = if self is Circle {
          |    self.radius() * self.radius() * 3.14
          |  } else if self is Square {
          |    self.side() * self.side()
          |  } else { 0.0 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Shape = new Circle(2.0);
          |    return "" + c.area();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("12.56") == result)
    }

    it("narrows this in the then-branch of if this is Subtype") {
      val result = shell.run(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |  case Square(side: Double)
          |}
          |extension Shape {
          |  def describe(): String = if this is Circle {
          |    "circle r=" + this.radius()
          |  } else if this is Square {
          |    "square s=" + this.side()
          |  } else { "other" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: Shape = new Square(3.0);
          |    return s.describe();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("square s=3.0") == result)
    }

    it("narrows self in the else-branch of if !(self is Subtype)") {
      val result = shell.run(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |  case Square(side: Double)
          |}
          |extension Shape {
          |  def area(): Double = if !(self is Circle) {
          |    0.0
          |  } else {
          |    self.radius() * self.radius() * 3.14
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Shape = new Circle(2.0);
          |    return "" + c.area();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("12.56") == result)
    }
  }
}
