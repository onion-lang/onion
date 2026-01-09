package onion.compiler.tools

import onion.tools.Shell

class ExpressionOrientedSpec extends AbstractShellSpec {
  describe("Expression-oriented synchronized") {
    it("returns Int value from synchronized body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val lock = new Object();
          |    val x: Int = synchronized(lock) { 42 };
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("returns String value from synchronized body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val lock = new Object();
          |    val s: String = synchronized(lock) { "hello" };
          |    return s;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("returns value from nested synchronized") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val lock1 = new Object();
          |    val lock2 = new Object();
          |    val x: Int = synchronized(lock1) {
          |      synchronized(lock2) { 100 }
          |    };
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(100) == result)
    }

    it("returns result of expression in synchronized body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val lock = new Object();
          |    var counter: Int = 10;
          |    val result: Int = synchronized(lock) { counter + 5 };
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("returns Long value from synchronized body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Long {
          |    val lock = new Object();
          |    val x: Long = synchronized(lock) { 123456789L };
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(123456789L) == result)
    }

    it("synchronized as statement still works") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val lock = new Object();
          |    var counter: Int = 0;
          |    synchronized(lock) {
          |      counter = counter + 1;
          |    }
          |    return counter;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }

  describe("Expression-oriented loops") {
    it("while expression as statement") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var counter: Int = 0
          |    while counter < 3 {
          |      counter = counter + 1
          |    }
          |    return counter
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("for expression as statement") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var sum: Int = 0
          |    var i: Int = 1
          |    while i <= 5 {
          |      sum = sum + i
          |      i = i + 1
          |    }
          |    return sum
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("foreach expression as statement") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list: List = ["A", "B", "C"]
          |    var result: String = ""
          |    foreach s:String in list {
          |      result = result + s
          |    }
          |    return result
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ABC") == result)
    }

    it("nested while loops") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var total: Int = 0
          |    var i: Int = 0
          |    while i < 3 {
          |      var j: Int = 0
          |      while j < 2 {
          |        total = total + 1
          |        j = j + 1
          |      }
          |      i = i + 1
          |    }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(6) == result)
    }
  }
}
