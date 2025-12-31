package onion.compiler.tools

import onion.tools.Shell

class TryFinallySpec extends AbstractShellSpec {
  describe("Try-Finally") {
    it("executes finally block after normal completion") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    try {
          |      result = 1;
          |    } finally {
          |      result = result + 10;
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "Test1.on",
        Array()
      )
      assert(Shell.Success(11) == result) // try: 1, finally: +10 = 11
    }

    it("executes finally block after exception is caught") {
      val result = shell.run(
        """
          |class Test2 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    try {
          |      result = 1;
          |      throw new Exception("test exception");
          |    } catch e: Exception {
          |      result = result + 10;
          |    } finally {
          |      result = result + 100;
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "Test2.on",
        Array()
      )
      assert(Shell.Success(111) == result) // try: 1, catch: +10, finally: +100 = 111
    }

    it("executes finally block even when exception is uncaught") {
      val result = shell.run(
        """
          |class Test3 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    try {
          |      try {
          |        result = 1;
          |        throw new RuntimeException("test");
          |      } finally {
          |        result = result + 10;
          |      }
          |    } catch e: RuntimeException {
          |      result = result + 100;
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "Test3.on",
        Array()
      )
      assert(Shell.Success(111) == result) // try: 1, finally: +10, catch: +100 = 111
    }

    it("executes finally block with return in catch") {
      val result = shell.run(
        """
          |class Test4 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    try {
          |      result = 1;
          |      throw new Exception("test");
          |    } catch e: Exception {
          |      result = result + 10;
          |      return result;
          |    } finally {
          |      result = result + 100;
          |    }
          |  }
          |}
          |""".stripMargin,
        "Test4.on",
        Array()
      )
      assert(Shell.Success(11) == result) // try: 1, catch: +10 = 11, finally should not affect return value
    }

    it("handles nested try-finally correctly") {
      val result = shell.run(
        """
          |class Test5 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    try {
          |      result = 1;
          |      try {
          |        result = result + 10;
          |      } finally {
          |        result = result + 100;
          |      }
          |    } finally {
          |      result = result + 1000;
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "Test5.on",
        Array()
      )
      assert(Shell.Success(1111) == result) // 1 + 10 + 100 + 1000 = 1111
    }

    it("supports resource cleanup pattern") {
      val result = shell.run(
        """
          |class Test6 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    var resource: Int = 0;
          |    try {
          |      resource = 1;
          |      result = 10;
          |    } finally {
          |      if (resource != 0) {
          |        result = result + resource;
          |      }
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "Test6.on",
        Array()
      )
      assert(Shell.Success(11) == result) // 10 + 1 = 11
    }

    it("works with try-catch-finally with no exception") {
      val result = shell.run(
        """
          |class Test7 {
          |public:
          |  static def main(args: String[]): Int {
          |    var result: Int = 0;
          |    try {
          |      result = 5;
          |    } catch e: Exception {
          |      result = 999;
          |    } finally {
          |      result = result + 3;
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "Test7.on",
        Array()
      )
      assert(Shell.Success(8) == result) // 5 + 3 = 8 (no exception)
    }
  }
}
