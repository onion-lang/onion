package onion.compiler.tools

import onion.tools.Shell

class SynchronizedSpec extends AbstractShellSpec {
  describe("Synchronized statement") {
    it("synchronizes on object reference") {
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

    it("synchronizes on this") {
      val result = shell.run(
        """
          |class Counter {
          |  var value: Int = 0;
          |public:
          |  def increment(): Int {
          |    synchronized(this) {
          |      this.value = this.value + 1;
          |    }
          |    return this.value;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val c = new Counter();
          |    c.increment();
          |    c.increment();
          |    return c.increment();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("synchronizes on string literal") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var result: String = "";
          |    synchronized("lock") {
          |      result = "synchronized";
          |    }
          |    return result;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("synchronized") == result)
    }

    it("rejects primitive type as lock") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    synchronized(123) {
          |      return 1;
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
