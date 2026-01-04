package onion.compiler.tools

import onion.tools.Shell

class NamedArgumentSpec extends AbstractShellSpec {
  describe("named arguments") {
    it("should support named arguments in order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def greet(name: String, greeting: String): String {
          |    return greeting + ", " + name;
          |  }
          |  static def main(args: String[]): String {
          |    return greet(name = "World", greeting = "Hello");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, World") == result)
    }

    it("should support named arguments out of order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def greet(name: String, greeting: String): String {
          |    return greeting + ", " + name;
          |  }
          |  static def main(args: String[]): String {
          |    return greet(greeting = "Hi", name = "Alice");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hi, Alice") == result)
    }

    it("should support mixed positional and named arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def format(a: Int, b: Int, c: Int): String {
          |    return "" + a + "," + b + "," + c;
          |  }
          |  static def main(args: String[]): String {
          |    return format(1, c = 3, b = 2);
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1,2,3") == result)
    }

    it("should work with named arguments and default values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def greet(name: String, greeting: String = "Hello", punctuation: String = "!"): String {
          |    return greeting + ", " + name + punctuation;
          |  }
          |  static def main(args: String[]): String {
          |    val r1 = greet(name = "World");
          |    val r2 = greet("Alice", punctuation = "?");
          |    val r3 = greet(punctuation = "...", name = "Bob");
          |    return r1 + "|" + r2 + "|" + r3;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, World!|Hello, Alice?|Hello, Bob...") == result)
    }

    it("should work with instance methods") {
      val result = shell.run(
        """
          |class Calculator {
          |public:
          |  def compute(x: Int, y: Int, op: String): Int {
          |    if (op == "+") { return x + y; }
          |    if (op == "*") { return x * y; }
          |    return 0;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val calc = new Calculator();
          |    val r1 = calc.compute(y = 5, x = 3, op = "+");
          |    val r2 = calc.compute(op = "*", x = 4, y = 6);
          |    return "" + r1 + "," + r2;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("8,24") == result)
    }
  }
}
