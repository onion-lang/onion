package onion.compiler.tools

import onion.tools.Shell

/**
 * Scala-3-style HETEROGENEOUS (ADT) enum cases. An `enum` whose cases use the
 * `case` keyword gives each case its OWN fields; it is desugared (Rewriting
 * phase) to a `sealed interface` + one `record` per case + singleton statics.
 * A `select` over `case x is X:` is then exhaustiveness-checked (E0042) for free.
 */
class AdtEnumSpec extends AbstractShellSpec {
  describe("ADT enum cases") {
    it("compiles and runs the Shape example (product + singleton cases)") {
      val r = shell.run(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |  case Square(side: Double)
          |  case Origin
          |public:
          |  def area(): Double = select this {
          |    case c is Circle: c.radius() * c.radius() * 3.14
          |    case s is Square: s.side() * s.side()
          |    case o is Origin: 0.0
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Double = (new Circle(2.0) as Shape).area()
          |    val b: Double = (new Square(3.0) as Shape).area()
          |    val c: Double = (new Origin() as Shape).area()
          |    return "" + a + "|" + b + "|" + c
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("12.56|9.0|0.0") == r)
    }

    it("supports a product-case accessor and construction") {
      val r = shell.run(
        """
          |enum Tree {
          |  case Leaf(value: Int)
          |  case Branch(left: Int, right: Int)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val leaf = new Leaf(42)
          |    val br = new Branch(1, 2)
          |    return "" + leaf.value() + "|" + br.left() + "|" + br.right()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("42|1|2") == r)
    }

    it("constructs a singleton case via new and matches it") {
      val r = shell.run(
        """
          |enum Color {
          |  case Red
          |  case Custom(rgb: Int)
          |public:
          |  def name(): String = select this {
          |    case r is Red: "red"
          |    case c is Custom: "custom"
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val red: Color = new Red()
          |    val cus: Color = new Custom(255)
          |    return red.name() + "|" + cus.name()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("red|custom") == r)
    }

    it("accepts semicolon-separated case clauses on a single line (CLAUDE.md-documented form)") {
      val r = shell.run(
        """
          |enum Shape { case Circle(radius: Double); case Square(side: Double); case Origin; public: def area(): Double = select this { case c is Circle: c.radius() * c.radius() * 3.14; case s is Square: s.side() * s.side(); case o is Origin: 0.0 } }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Double = (new Circle(2.0) as Shape).area()
          |    val b: Double = (new Square(3.0) as Shape).area()
          |    val c: Double = (new Origin() as Shape).area()
          |    return "" + a + "|" + b + "|" + c
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("12.56|9.0|0.0") == r)
    }

    it("reports E0042 when a select over the ADT enum omits a case") {
      val r = shell.run(
        """
          |enum Shape {
          |  case Circle(radius: Double)
          |  case Square(side: Double)
          |  case Origin
          |public:
          |  def area(): Double = select this {
          |    case c is Circle: c.radius() * c.radius()
          |    case s is Square: s.side() * s.side()
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "x" }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }

    it("allows explicit return in every branch of an ADT enum method (no operand-stack underflow)") {
      val r = shell.run(
        """
          |enum R {
          |  case Found(n: Int)
          |  case None
          |public:
          |  def describe(): String = select this {
          |    case f is Found: return "found " + f.n()
          |    case r is None: return "none"
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r1: R = new Found(4)
          |    val r2: R = new None()
          |    return r1.describe() + "|" + r2.describe()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("found 4|none") == r)
    }

    it("allows explicit return of a Boolean in every branch (primitive return via StatementTerm)") {
      val r = shell.run(
        """
          |enum R {
          |  case Found(n: Int)
          |  case None
          |public:
          |  def isFound(): Boolean = select this {
          |    case f is Found: return true
          |    case u is None: return false
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r1: R = new Found(4)
          |    val r2: R = new None()
          |    return "" + r1.isFound() + "|" + r2.isFound()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("true|false") == r)
    }
  }
}
