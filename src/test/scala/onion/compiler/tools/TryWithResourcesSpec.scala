package onion.compiler.tools

import onion.tools.Shell

class TryWithResourcesSpec extends AbstractShellSpec {
  describe("try-with-resources statement") {
    it("should close AutoCloseable resource automatically") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class SimpleResource <: AutoCloseable {
          |  var result: StringBuilder;
          |public:
          |  def this(r: StringBuilder) {
          |    this.result = r;
          |  }
          |  def close(): Unit {
          |    this.result.append("closed");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val result = new StringBuilder();
          |    try (val res = new SimpleResource(result)) {
          |      result.append("working,");
          |    }
          |    return result.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("working,closed") == result)
    }

    it("should close single resource") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable <: AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closed");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    log.append("start,");
          |    try (val a = new MyCloseable(log)) {
          |      log.append("try,");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("start,try,closed") == result)
    }

    it("should close resource before catch block executes") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable <: AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("close,");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val res = new MyCloseable(log)) {
          |      log.append("try,");
          |      throw new RuntimeException("error");
          |    } catch e: RuntimeException {
          |      log.append("catch");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("try,close,catch") == result)
    }

    it("should execute finally after resource close") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable <: AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("close,");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val res = new MyCloseable(log)) {
          |      log.append("try,");
          |    } finally {
          |      log.append("finally");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("try,close,finally") == result)
    }

    it("should close resource even when exception is thrown") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable <: AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closed");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      try (val res = new MyCloseable(log)) {
          |        throw new RuntimeException("error");
          |      }
          |    } catch e: RuntimeException {
          |      // Outer catch
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("closed") == result)
    }
  }
}
