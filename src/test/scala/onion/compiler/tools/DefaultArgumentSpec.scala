package onion.compiler.tools

import onion.tools.Shell

class DefaultArgumentSpec extends AbstractShellSpec {
  describe("default arguments") {
    it("still allows a default that calls a static method of the class") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def g(n: Int): Int = n
          |  def f(n: Int, m: Int = g(2)): Int = n + m
          |  static def main(args: String[]): String = "" + new C().f(1)
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("3") == result)
    }

    it("should use default value when argument is omitted") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def greet(name: String, greeting: String = "Hello"): String {
          |    return greeting + ", " + name;
          |  }
          |  static def main(args: String[]): String {
          |    return greet("World");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, World") == result)
    }

    it("should use provided value when argument is given") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def greet(name: String, greeting: String = "Hello"): String {
          |    return greeting + ", " + name;
          |  }
          |  static def main(args: String[]): String {
          |    return greet("World", "Hi");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hi, World") == result)
    }

    it("should support multiple default arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def format(a: Int, b: Int = 10, c: Int = 100): Int {
          |    return a + b + c;
          |  }
          |  static def main(args: String[]): String {
          |    val r1 = format(1);
          |    val r2 = format(1, 20);
          |    val r3 = format(1, 20, 300);
          |    return "" + r1 + "," + r2 + "," + r3;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("111,121,321") == result)
    }

    it("should work with instance methods") {
      val result = shell.run(
        """
          |class Greeter {
          |public:
          |  def greet(name: String, greeting: String = "Hello"): String {
          |    return greeting + ", " + name + "!";
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val g = new Greeter();
          |    return g.greet("Alice");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, Alice!") == result)
    }

    it("should work with different types of default values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def compute(x: Int, multiplier: Int = 2, enabled: Boolean = true): String {
          |    if (enabled) {
          |      val result: Int = x * multiplier;
          |      return "" + result;
          |    } else {
          |      return "disabled";
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    val r1 = compute(10);
          |    val r2 = compute(10, 3);
          |    val r3 = compute(10, 3, false);
          |    return r1 + "," + r2 + "," + r3;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("20,30,disabled") == result)
    }

    it("should work with string default values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def wrap(s: String, prefix: String = "[", suffix: String = "]"): String {
          |    return prefix + s + suffix;
          |  }
          |  static def main(args: String[]): String {
          |    val r1 = wrap("test");
          |    val r2 = wrap("test", "<");
          |    val r3 = wrap("test", "<", ">");
          |    return r1 + "," + r2 + "," + r3;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // r1 = [test] (both defaults)
      // r2 = <test] (prefix="<", suffix defaults to "]")
      // r3 = <test> (both provided)
      assert(Shell.Success("[test],<test],<test>") == result)
    }

    // #2358 asked for this not to crash with VerifyError. #2360 made it E0002; a default now
    // runs in the callee (#2375), so the earlier parameter is in scope and g(1) is 1 + 2.
    it("accepts a default that uses an earlier parameter instead of crashing with VerifyError") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def g(n: Int, m: Int = n + 1): Int { return n + m; }
          |  static def main(args: String[]): Int { return g(1); }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
  }
}
