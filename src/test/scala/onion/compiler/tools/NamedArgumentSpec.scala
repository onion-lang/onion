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

  describe("constructor named arguments") {
    it("should support named arguments in constructor") {
      val result = shell.run(
        """
          |class Person {
          |public:
          |  var name: String
          |  var age: Int
          |  def this(name: String, age: Int) {
          |    this.name = name;
          |    this.age = age;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person(age = 30, name = "Alice");
          |    return p.name + ":" + p.age;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice:30") == result)
    }

    it("should support mixed positional and named args in constructor") {
      val result = shell.run(
        """
          |class Point {
          |public:
          |  var x: Int
          |  var y: Int
          |  var z: Int
          |  def this(x: Int, y: Int, z: Int) {
          |    this.x = x;
          |    this.y = y;
          |    this.z = z;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, z = 3, y = 2);
          |    return "" + p.x + "," + p.y + "," + p.z;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1,2,3") == result)
    }

    it("should support default values with named args in constructor") {
      val result = shell.run(
        """
          |class Config {
          |public:
          |  var host: String
          |  var port: Int
          |  var timeout: Int
          |  def this(host: String, port: Int = 8080, timeout: Int = 30) {
          |    this.host = host;
          |    this.port = port;
          |    this.timeout = timeout;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c1 = new Config(host = "localhost");
          |    val c2 = new Config("example.com", timeout = 60);
          |    val c3 = new Config(timeout = 10, host = "server");
          |    return c1.host + ":" + c1.port + ":" + c1.timeout + "|" +
          |           c2.host + ":" + c2.port + ":" + c2.timeout + "|" +
          |           c3.host + ":" + c3.port + ":" + c3.timeout;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("localhost:8080:30|example.com:8080:60|server:8080:10") == result)
    }

    it("should work with record constructors") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person(age = 25, name = "Bob");
          |    return p.name() + ":" + p.age();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Bob:25") == result)
    }
  }
}
