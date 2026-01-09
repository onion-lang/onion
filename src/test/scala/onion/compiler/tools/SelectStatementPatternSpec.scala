package onion.compiler.tools

import onion.tools.Shell

class SelectStatementPatternSpec extends AbstractShellSpec {
  describe("Select statement with type pattern") {
    it("matches type pattern in statement context") {
      val result = shell.run(
        """
          |sealed interface Animal {}
          |record Dog(name: String) <: Animal
          |record Cat(name: String) <: Animal
          |
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val animal: Animal = new Dog("Rex");
          |    var result: String = "";
          |    select animal {
          |      case d is Dog: result = "dog: " + d.name();
          |      case c is Cat: result = "cat: " + c.name();
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("dog: Rex") == result)
    }

    it("handles multiple type patterns in statement") {
      val result = shell.run(
        """
          |sealed interface Shape {}
          |record Circle(radius: Int) <: Shape
          |record Square(side: Int) <: Shape
          |record Triangle(base: Int, height: Int) <: Shape
          |
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val shape: Shape = new Triangle(10, 5);
          |    var area: Int = 0;
          |    select shape {
          |      case c is Circle: area = c.radius() * c.radius();
          |      case s is Square: area = s.side() * s.side();
          |      case t is Triangle: area = t.base() * t.height() / 2;
          |    }
          |    return area;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(25) == result)
    }
  }

  describe("Select statement with destructuring pattern") {
    it("destructures record in statement context") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val p: Point = new Point(3, 4);
          |    var sum: Int = 0;
          |    select p {
          |      case Point(x, y): sum = x + y;
          |    }
          |    return sum;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("destructures sealed interface subtypes in statement") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: Int) <: Result
          |record Error(message: String) <: Result
          |
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Result = new Success(42);
          |    var output: Int = 0;
          |    select r {
          |      case Success(v): output = v;
          |      case Error(m): output = -1;
          |    }
          |    return output;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("Select statement with guarded pattern") {
    it("applies guard condition in statement context with binding pattern") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Int = 15;
          |    var category: String = "";
          |    select n {
          |      case x when x < 0: category = "negative";
          |      case x when x < 10: category = "small";
          |      case x when x < 100: category = "medium";
          |      else: category = "large";
          |    }
          |    return category;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("medium") == result)
    }

    it("combines type pattern with guard in statement") {
      val result = shell.run(
        """
          |sealed interface Box {}
          |record IntBox(value: Int) <: Box
          |record StringBox(value: String) <: Box
          |
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val box: Box = new IntBox(50);
          |    var result: String = "";
          |    select box {
          |      case b is IntBox when b.value() > 100: result = "large int";
          |      case b is IntBox when b.value() > 10: result = "medium int";
          |      case b is IntBox: result = "small int";
          |      case s is StringBox: result = "string";
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("medium int") == result)
    }
  }

  describe("Select statement with wildcard pattern") {
    it("matches wildcard in statement context") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 99;
          |    var result: String = "";
          |    select x {
          |      case 1: result = "one";
          |      case 2: result = "two";
          |      case _: result = "other";
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("other") == result)
    }
  }

  describe("Select statement with mixed patterns") {
    it("combines expression, type, and wildcard patterns") {
      val result = shell.run(
        """
          |sealed interface Value {}
          |record IntVal(n: Int) <: Value
          |record StrVal(s: String) <: Value
          |
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v: Value = new StrVal("hello");
          |    var result: String = "";
          |    select v {
          |      case i is IntVal: result = "int: " + i.n();
          |      case s is StrVal: result = "str: " + s.s();
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("str: hello") == result)
    }
  }
}
